package com.example.lifeplanner.desktop.ui;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import com.example.lifeplanner.desktop.api.ApiClient;
import com.example.lifeplanner.desktop.api.Models.ProjectDay;
import com.example.lifeplanner.desktop.api.Models.ProjectDetail;
import com.example.lifeplanner.desktop.api.Models.ProjectSummary;
import com.example.lifeplanner.desktop.api.Models.ProjectsOverview;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/** Projelerin özet metrikleri: üstte toplamlar, altında proje kartları; karta tıklayınca detay paneli. */
public class ProjectsPage extends Page {

	private static final Map<String, String> HEALTH_LABEL = Map.of("OK", "Çalışıyor", "WARNING", "Uyarı", "DOWN", "Kesinti");
	private static final Map<String, Color> HEALTH_COLOR = Map.of("OK", Color.web("#a78bfa"), "WARNING", Color.web("#fbbf24"),
			"DOWN", Color.web("#f87171"));

	private final ApiClient api;
	private final VBox body = new VBox(16);
	private final Label updated = Ui.label("", "muted");
	private final Label mockTag = Ui.tag("örnek veri", "overdue");
	private String selected;

	public ProjectsPage(ApiClient api) {
		this.api = api;
		mockTag.setVisible(false);
		getChildren().addAll(Ui.row(Ui.label("🚀 Projeler", "h1"), Ui.spacer(), mockTag, updated),
				Ui.label("Projelerinin özet metrikleri. Ayrıntı için karta tıkla; her projenin kendi dashboard'u da bir tık uzakta.", "subtitle"),
				body);
	}

	@Override
	public void refresh() {
		Ui.async(api::projects, this::render);
	}

	private void render(ProjectsOverview o) {
		updated.setText("Son güncelleme: " + Money.timeAgo(o.generatedAt()));
		mockTag.setVisible(o.projects().stream().anyMatch(ProjectSummary::mock));
		if (o.projects().isEmpty()) {
			body.getChildren().setAll(Ui.card(Ui.label("Takip edilen proje yok", "h2"),
					Ui.label("Obsidian'da Projeler/Projeler.md tablosuna proje ekle. En geç 5 dakika içinde burada görünür.", "muted")));
			return;
		}
		// Ekran görüntüsü modunda detay paneli de görünsün
		if (System.getProperty("lifeplanner.snapshot") != null && selected == null) {
			selected = o.projects().stream().filter(p -> !"OK".equals(p.health())).findFirst().orElse(o.projects().get(0)).slug();
		}
		var t = o.totals();
		var healthText = t.down() > 0 ? t.down() + " projede kesinti" : t.warning() > 0 ? t.warning() + " projede uyarı" : "Hepsi sağlıklı";
		var stats = new HBox(12,
				stat("Toplam aktif kullanıcı (bugün)", Ui.label(num(t.activeUsers()), "stat-value"),
						Ui.label(pct(t.activeUsersChangePct()) + " geçen haftaya göre", pctCls(t.activeUsersChangePct()))),
				stat("Aylık aktif kullanıcı", Ui.label(num(t.mau()), "stat-value", "plain"), Ui.label(t.projects() + " projenin toplamı", "muted")),
				stat("Aylık gelir (MRR)", Ui.label(Money.money(t.mrr(), "TRY", 0), "stat-value", "plain"), Ui.label("gelir elde eden projeler", "muted")),
				stat("Sağlık", Ui.label(t.healthy() + " / " + t.projects(), "stat-value", "plain"),
						Ui.label(healthText, t.down() > 0 ? "neg" : t.warning() > 0 ? "warn" : "pos")));

		var grid = new TilePane(16, 16);
		grid.setPrefColumns(3);
		grid.setPrefTileWidth(300);
		grid.setTileAlignment(Pos.TOP_LEFT);
		o.projects().forEach(p -> grid.getChildren().add(card(p)));
		body.getChildren().setAll(stats, grid);
		if (selected != null && o.projects().stream().anyMatch(p -> p.slug().equals(selected))) {
			loadDetail(selected);
		}
	}

	private VBox stat(String label, Node value, Node sub) {
		var c = Ui.card(Ui.label(label, "stat-label"), value, sub);
		c.setSpacing(4);
		HBox.setHgrow(c, Priority.ALWAYS);
		c.setMaxWidth(Double.MAX_VALUE);
		return c;
	}

	private Node card(ProjectSummary p) {
		var name = Ui.label(p.name(), "h2");
		var head = Ui.row(name, healthTag(p.health()));
		var desc = Ui.label(String.join(" · ", java.util.stream.Stream.of(p.description(), p.platform()).filter(s -> s != null).toList()), "muted");
		desc.setWrapText(true);
		var big = Ui.label(num(p.activeUsers()), "big-num");
		var change = Ui.label(pct(p.activeUsersChangePct()), pctCls(p.activeUsersChangePct()));
		var main = Ui.row(big, Ui.label("aktif kullanıcı", "muted"), change);
		main.setAlignment(Pos.BASELINE_LEFT);

		var kv = new GridPane();
		kv.setHgap(10);
		kv.setVgap(8);
		for (int i = 0; i < 3; i++) {
			var c = new ColumnConstraints();
			c.setPercentWidth(33.3);
			kv.getColumnConstraints().add(c);
		}
		kv.add(kv("MAU", num(p.mau()), null), 0, 0);
		kv.add(kv("Yeni kayıt (7g)", num(p.signups7d()), null), 1, 0);
		kv.add(kv("MRR", p.mrr() != null ? Money.money(p.mrr(), "TRY", 0) : "—", null), 2, 0);
		kv.add(kv("Uptime (30g)", uptime(p.uptime30d()), p.uptime30d() < 99.5 ? "warn" : null), 0, 1);
		kv.add(kv("Açık hata", String.valueOf(p.openIssues()), p.openIssues() >= 10 ? "neg" : null), 1, 1);
		kv.add(kv("Son deploy", Money.timeAgo(p.lastDeployAt()), null), 2, 1);

		var card = Ui.card(head, desc, main, Charts.sparkline(p.sparkline(), HEALTH_COLOR.get(p.health())), kv);
		if (!p.healthReasons().isEmpty()) {
			var reason = Ui.label(p.healthReasons().get(0) + (p.healthReasons().size() > 1 ? " (+" + (p.healthReasons().size() - 1) + ")" : ""),
					"DOWN".equals(p.health()) ? "neg" : "warn");
			reason.setWrapText(true);
			card.getChildren().add(reason);
		}
		if (p.dashboardUrl() != null) {
			var link = new Hyperlink("Dashboard'u aç ↗");
			link.setOnAction(e -> {
				e.consume();
				Ui.openUrl(p.dashboardUrl());
			});
			card.getChildren().add(link);
		}
		card.getStyleClass().addAll("project-card", "project-" + p.health().toLowerCase());
		if (p.slug().equals(selected)) {
			card.getStyleClass().add("selected");
		}
		card.setOnMouseClicked(e -> {
			selected = p.slug().equals(selected) ? null : p.slug();
			refresh();
		});
		return card;
	}

	private static VBox kv(String label, String value, String valueClass) {
		var v = valueClass != null ? Ui.label(value, "kv-value", valueClass) : Ui.label(value, "kv-value");
		return new VBox(1, Ui.label(label, "muted"), v);
	}

	private void loadDetail(String slug) {
		Ui.async(() -> api.project(slug), d -> {
			body.getChildren().removeIf(n -> n.getStyleClass().contains("project-detail"));
			body.getChildren().add(detail(d));
		});
	}

	private Node detail(ProjectDetail d) {
		var s = d.summary();
		var header = Ui.row(Ui.label(s.name(), "h2"), healthTag(s.health()));
		if (s.mock()) {
			header.getChildren().add(Ui.tag("örnek veri", "overdue"));
		}
		header.getChildren().add(Ui.spacer());
		if (s.dashboardUrl() != null) {
			header.getChildren().add(Ui.button("Dashboard'u aç ↗", () -> Ui.openUrl(s.dashboardUrl())));
		}
		header.getChildren().add(Ui.button("Kapat", () -> {
			selected = null;
			refresh();
		}));
		var card = Ui.card(header);
		card.getStyleClass().add("project-detail");
		s.healthReasons().forEach(r -> card.getChildren().add(Ui.label("•  " + r, "DOWN".equals(s.health()) ? "neg" : "warn")));

		var dates = d.daily().stream().map(ProjectDay::date).toList();
		var charts = new TilePane(16, 12);
		charts.setPrefColumns(3);
		charts.setPrefTileWidth(300);
		charts.getChildren().add(chartBox("Aktif kullanıcı · son 30 gün", Charts.chart(Charts.Kind.LINE, dates,
				d.daily().stream().map(x -> (double) x.activeUsers()).toList(), HEALTH_COLOR.get(s.health()), v -> num(Math.round(v)))));
		charts.getChildren().add(chartBox("Yeni kayıt · günlük", Charts.chart(Charts.Kind.BAR, dates,
				d.daily().stream().map(x -> (double) x.signups()).toList(), Color.web("#22d3ee"), v -> num(Math.round(v)))));
		if (d.daily().stream().anyMatch(x -> x.revenue() != null)) {
			charts.getChildren().add(chartBox("Gelir · günlük", Charts.chart(Charts.Kind.LINE, dates,
					d.daily().stream().map(x -> x.revenue() != null ? x.revenue().doubleValue() : 0.0).toList(), Color.web("#4ade80"),
					v -> Money.money(BigDecimal.valueOf(v), "TRY", 0))));
		}
		card.getChildren().add(charts);

		var fmt = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Ui.TR).withZone(java.time.ZoneId.systemDefault());
		var table = new GridPane();
		table.setHgap(16);
		table.setVgap(6);
		var c0 = new ColumnConstraints();
		c0.setHgrow(Priority.ALWAYS);
		table.getColumnConstraints().add(c0);
		int r = 0;
		r = metricRow(table, r, "Aktif kullanıcı (bugün)", num(s.activeUsers()), s.activeUsersChangePct());
		r = metricRow(table, r, "Aylık aktif kullanıcı (MAU)", num(s.mau()), s.mauChangePct());
		r = metricRow(table, r, "Yeni kayıt (son 7 gün)", num(s.signups7d()), null);
		r = metricRow(table, r, "Aylık gelir (MRR)", s.mrr() != null ? Money.money(s.mrr(), "TRY", 0) : "—", s.mrr() != null ? s.mrrChangePct() : null);
		r = metricRow(table, r, "Erişilebilirlik (30 gün)", uptime(s.uptime30d()), null);
		r = metricRow(table, r, "Hata oranı (24 saat)", "%" + String.format(Ui.TR, "%.1f", s.errorRate24h()), null);
		r = metricRow(table, r, "Açık hata", String.valueOf(s.openIssues()), null);
		metricRow(table, r, "Son deploy", fmt.format(s.lastDeployAt()) + " (" + Money.timeAgo(s.lastDeployAt()) + ")", null);
		card.getChildren().add(table);
		return card;
	}

	private static int metricRow(GridPane t, int r, String label, String value, Double change) {
		t.add(Ui.label(label, "muted"), 0, r);
		var v = Ui.row(Ui.label(value));
		v.setAlignment(Pos.CENTER_RIGHT);
		if (change != null) {
			v.getChildren().add(Ui.label(pct(change), pctCls(change)));
		}
		t.add(v, 1, r);
		return r + 1;
	}

	private static VBox chartBox(String title, Node chart) {
		var box = new VBox(6, Ui.label(title, "muted"), chart);
		box.setMinWidth(0);
		return box;
	}

	private static Label healthTag(String health) {
		return Ui.label(HEALTH_LABEL.getOrDefault(health, health), "health", "health-" + health.toLowerCase());
	}

	private static String num(long v) {
		return java.text.NumberFormat.getIntegerInstance(Ui.TR).format(v);
	}

	private static String pct(Double v) {
		if (v == null) {
			return "";
		}
		return (v > 0 ? "+" : v < 0 ? "−" : "") + "%" + String.format(Ui.TR, "%.1f", Math.abs(v)).replace(",0", "");
	}

	private static String pctCls(Double v) {
		return v == null || v == 0 ? "muted" : v > 0 ? "pos" : "neg";
	}

	private static String uptime(double v) {
		return "%" + java.text.NumberFormat.getNumberInstance(Ui.TR).format(v);
	}
}
