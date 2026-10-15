package com.example.lifeplanner.desktop.ui;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.example.lifeplanner.desktop.api.ApiClient;
import com.example.lifeplanner.desktop.api.Models.CategoryShare;
import com.example.lifeplanner.desktop.api.Models.SpendingCategory;
import com.example.lifeplanner.desktop.api.Models.StatementDetail;
import com.example.lifeplanner.desktop.api.Models.StatementSummary;
import com.example.lifeplanner.desktop.api.Models.StatementTx;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Finans → Ödemeler → Hesap özetleri: kredi kartı ekstresi yükleme, harcama dağılımı, taksitler ve sınıflandırma. */
public class StatementsPane extends VBox {

	private static final String[] NEW_COLORS = { "#f59e0b", "#10b981", "#6366f1", "#ec4899", "#14b8a6", "#8b5cf6", "#ef4444", "#84cc16" };
	private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", Ui.TR);
	private static final DateTimeFormatter SHORT_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", Ui.TR);
	/** Kategori seçicisinde "sınıfsız" ve "+ Yeni sınıf" için özel kimlikler. */
	private static final long NONE = -1, NEW = -2;

	private final ApiClient api;
	private final FileDropZone drop;
	private final ComboBox<StatementSummary> picker = new ComboBox<>();
	private final Button deleteButton;
	private final VBox content = new VBox(16);
	private final Label notice = Ui.label("", "notice");
	private List<SpendingCategory> categories = List.of();
	private StatementDetail detail;
	/** null = tümü, NONE = sınıflandırılmamış, -3 = ödemeler, diğer = kategori kimliği */
	private Long filter;
	private String query = "";
	/** Sınıfı değiştirilmekte olan işlem ve seçilen yeni sınıf (null = sınıfsız). */
	private Long editingTx;
	private Long editingCategory;
	private boolean rulesOpen;

	public StatementsPane(ApiClient api) {
		super(16);
		this.api = api;
		drop = new FileDropZone("Hesap özeti PDF'ini buraya bırak ya da tıkla", List.of("pdf"), this::upload);
		HBox.setHgrow(drop, Priority.ALWAYS);
		drop.setMaxWidth(Double.MAX_VALUE);

		picker.setConverter(TaskWidgets.converter(s -> s == null ? "" : s.bank() + " ····" + Objects.requireNonNullElse(s.cardLast4(), "????")
				+ " · " + (s.periodEnd() != null ? Ui.cap(s.periodEnd().format(MONTH_YEAR)) : s.fileName())));
		picker.setMaxWidth(Double.MAX_VALUE);
		picker.setOnAction(e -> {
			if (picker.getValue() != null && (detail == null || !picker.getValue().id().equals(detail.summary().id()))) {
				filter = null;
				loadDetail(picker.getValue().id());
			}
		});
		deleteButton = Ui.button("Sil", () -> {
			if (detail != null && Ui.confirm("Bu hesap özeti ve işlemleri silinsin mi? (Dosya da silinir)")) {
				Ui.run(() -> api.deleteStatement(detail.summary().id()), () -> {
					detail = null;
					refresh();
				});
			}
		}, "ghost", "del");
		var pickerCard = Ui.card(Ui.label("Hesap özeti", "muted"), picker, deleteButton);
		pickerCard.setMinWidth(300);
		pickerCard.setMaxWidth(320);
		pickerCard.setAlignment(Pos.CENTER_LEFT);

		notice.setVisible(false);
		notice.setManaged(false);
		notice.setOnMouseClicked(e -> showNotice(null));
		getChildren().addAll(new HBox(16, drop, pickerCard), notice, content);
	}

	public void refresh() {
		Ui.async(() -> new Object[] { api.statements(), api.spendingCategories() }, r -> {
			@SuppressWarnings("unchecked") var list = (List<StatementSummary>) r[0];
			@SuppressWarnings("unchecked") var cats = (List<SpendingCategory>) r[1];
			categories = cats;
			var currentId = detail != null ? detail.summary().id() : null;
			picker.getItems().setAll(list);
			var select = list.stream().filter(s -> s.id().equals(currentId)).findFirst().orElse(list.isEmpty() ? null : list.get(0));
			picker.setValue(select);
			picker.getParent().setVisible(!list.isEmpty());
			if (select == null) {
				detail = null;
				content.getChildren().setAll(Ui.card(Ui.label("Henüz hesap özeti yok", "h2"),
						Ui.label("Bankanın gönderdiği PDF ekstreyi yükle; işlemler okunup sınıflara ayrılır, taksitlerin listelenir.", "muted")));
			}
			else {
				loadDetail(select.id());
			}
		});
	}

	private void upload(File file) {
		drop.setBusy(true);
		showNotice(null);
		Ui.async(() -> new Object[] { api.uploadStatement(file.toPath()), api.statements(), api.spendingCategories() }, r -> {
			drop.setBusy(false);
			var s = (StatementSummary) r[0];
			@SuppressWarnings("unchecked") var list = (List<StatementSummary>) r[1];
			@SuppressWarnings("unchecked") var cats = (List<SpendingCategory>) r[2];
			showNotice(s.duplicate() ? "Bu hesap özeti daha önce yüklenmiş; mevcut kayıt açıldı."
					: s.bank() + " hesap özeti okundu: " + s.transactionCount() + " işlem.");
			categories = cats;
			detail = null;
			filter = null;
			picker.getItems().setAll(list);
			picker.getParent().setVisible(true);
			list.stream().filter(x -> x.id().equals(s.id())).findFirst().ifPresent(picker::setValue);
			loadDetail(s.id());
		}, () -> drop.setBusy(false));
	}

	private void showNotice(String text) {
		notice.setText(text == null ? "" : text);
		notice.setVisible(text != null);
		notice.setManaged(text != null);
	}

	private void loadDetail(long id) {
		Ui.async(() -> api.statement(id), d -> {
			detail = d;
			render();
		});
	}

	// =====================================================================
	private void render() {
		var d = detail;
		var s = d.summary();
		var nodes = new ArrayList<Node>();

		var warnings = new ArrayList<String>();
		if (s.experimental()) {
			warnings.add(s.bank() + " ekstre okuyucusu deneysel; işlemleri kontrol et.");
		}
		warnings.addAll(d.warnings());
		if (!warnings.isEmpty()) {
			var box = new VBox(4);
			box.getStyleClass().add("warn-box");
			warnings.forEach(w -> box.getChildren().add(wrap("⚠️ " + w, "warn")));
			nodes.add(box);
		}

		var inst = d.installments();
		var spendingCount = d.transactions().stream().filter(t -> !"PAYMENT".equals(t.kind())).count();
		var checkText = d.check() == null ? "" : d.check().matches() ? "✓ İşlemlerin toplamı ekstreyle tutuyor"
				: "Okunan: " + Money.money(d.check().parsed(), "TRY");
		var unclassifiedCard = stat("Sınıflandırılmamış", Ui.label(String.valueOf(s.unclassifiedCount()), "stat-value", "plain",
				s.unclassifiedCount() > 0 ? "warn" : "pos"), Ui.label(spendingCount + " harcama işleminden", "muted"));
		unclassifiedCard.getStyleClass().add("clickable");
		unclassifiedCard.setOnMouseClicked(e -> {
			filter = NONE;
			render();
		});
		nodes.add(new HBox(12,
				stat("Dönem borcu", Ui.label(Money.money(s.totalDue(), "TRY", 0), "stat-value"),
						Ui.label(checkText, d.check() != null && d.check().matches() ? "pos" : "warn")),
				stat("Son ödeme", Ui.label(s.dueDate() != null ? s.dueDate().format(Ui.DAY_MONTH) : "—", "stat-value", "plain"),
						Ui.label("Kesim: " + (s.periodEnd() != null ? s.periodEnd().format(Ui.DAY_MONTH) : "—"), "muted")),
				stat("Taksitler (gelecek ay)", Ui.label(Money.money(inst.nextMonthTotal(), "TRY", 0), "stat-value", "plain"),
						Ui.label(inst.active() + " aktif taksit · kalan " + Money.money(inst.remainingTotal(), "TRY", 0), "muted")),
				unclassifiedCard));

		var breakdown = breakdownCard(d, spendingCount);
		var installments = installmentsCard(d);
		HBox.setHgrow(breakdown, Priority.ALWAYS);
		HBox.setHgrow(installments, Priority.ALWAYS);
		breakdown.setPrefWidth(420);
		installments.setPrefWidth(520);
		var row = new HBox(16, breakdown, installments);
		nodes.add(row);
		nodes.add(transactionsCard(d));
		nodes.add(rulesCard());
		content.getChildren().setAll(nodes);
	}

	private VBox stat(String label, Node value, Node sub) {
		var c = Ui.card(Ui.label(label, "stat-label"), value, sub);
		c.setSpacing(4);
		HBox.setHgrow(c, Priority.ALWAYS);
		c.setMaxWidth(Double.MAX_VALUE);
		if (sub instanceof Label l) {
			l.setWrapText(true);
		}
		return c;
	}

	private VBox breakdownCard(StatementDetail d, long count) {
		var positive = d.breakdown().stream().filter(b -> b.amount().signum() > 0).toList();
		var donut = new Donut(positive.stream().map(b -> new Donut.Slice(b.amount().doubleValue(), b.color())).toList(), 190, 22,
				Money.money(d.summary().spending(), "TRY", 0), count + " işlem");
		var legend = new VBox(0);
		for (CategoryShare b : d.breakdown()) {
			long key = b.categoryId() == null ? NONE : b.categoryId();
			var r = Ui.row(dot(b.color()), Ui.label(b.name()), Ui.label("(" + b.count() + ")", "muted"), Ui.spacer(),
					Ui.label(Money.money(b.amount(), "TRY", 0), "muted"), Ui.label("%" + Money.num(b.percent()), "h2"));
			r.getStyleClass().addAll("item", "clickable");
			if (Objects.equals(filter, key)) {
				r.getStyleClass().add("selected-row");
			}
			r.setOnMouseClicked(e -> {
				filter = key;
				render();
			});
			legend.getChildren().add(r);
		}
		var card = Ui.card(Ui.label("Harcamalar nereye gitti?", "h2"), donut, legend);
		card.setAlignment(Pos.TOP_CENTER);
		return card;
	}

	private VBox installmentsCard(StatementDetail d) {
		var grid = new GridPane();
		grid.setHgap(12);
		String[] heads = { "İşlem", "Taksit", "Aylık", "Kalan", "Bitiş" };
		for (int i = 0; i < heads.length; i++) {
			var c = new ColumnConstraints();
			c.setHalignment(i == 0 ? HPos.LEFT : HPos.RIGHT);
			if (i == 0) {
				c.setHgrow(Priority.ALWAYS);
			}
			grid.getColumnConstraints().add(c);
			grid.add(Ui.label(heads[i], "muted"), i, 0);
		}
		int r = 1;
		for (var i : d.installments().items()) {
			boolean done = i.remainingInstallments() == 0;
			var name = new VBox(1, wrap(i.merchant(), null), Ui.label(i.purchaseDate().format(Ui.SHORT)
					+ (i.purchaseTotal() != null ? " · toplam " + Money.money(i.purchaseTotal(), "TRY", 0) : ""), "muted"));
			Node remaining = done ? Ui.label("bitti", "pos")
					: new VBox(1, Ui.label(Money.money(i.remainingAmount(), "TRY", 0)), Ui.label(i.remainingInstallments() + " taksit", "muted"));
			Node[] cells = { name, Ui.label(i.installmentNo() + "/" + i.installmentCount()), Ui.label(Money.money(i.monthly(), "TRY")),
					remaining, Ui.label(!done && i.endsIn() != null ? Ui.cap(i.endsIn().format(SHORT_YEAR)) : "—") };
			for (int c = 0; c < cells.length; c++) {
				var cell = new VBox(cells[c]);
				cell.setAlignment(c == 0 ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);
				cell.getStyleClass().add("table-cell");
				cell.setOpacity(done ? 0.5 : 1);
				grid.add(cell, c, r);
			}
			r++;
		}
		var card = Ui.card(Ui.label("Taksitler", "h2"));
		card.getChildren().add(d.installments().items().isEmpty() ? Ui.empty("Taksitli işlem yok") : grid);
		return card;
	}

	private VBox transactionsCard(StatementDetail d) {
		var search = new TextField(query);
		search.setPromptText("Ara…");
		search.setPrefWidth(180);
		search.textProperty().addListener((o, a, b) -> {
			query = b;
			render();
			javafx.application.Platform.runLater(() -> {
				var field = findSearch();
				if (field != null) {
					field.requestFocus();
					field.positionCaret(field.getText().length());
				}
			});
		});
		search.getStyleClass().add("tx-search");

		var filterBox = new ComboBox<Long>();
		filterBox.getItems().add(0L);
		filterBox.getItems().add(NONE);
		filterBox.getItems().add(-3L);
		categories.forEach(c -> filterBox.getItems().add(c.id()));
		var catById = categories.stream().collect(Collectors.toMap(SpendingCategory::id, Function.identity()));
		filterBox.setConverter(TaskWidgets.converter(v -> v == null || v == 0L ? "Tümü (" + d.transactions().size() + ")"
				: v == NONE ? "Sınıflandırılmamış (" + d.summary().unclassifiedCount() + ")"
						: v == -3L ? "Ödemeler" : catById.containsKey(v) ? catById.get(v).name() : "?"));
		filterBox.setValue(filter == null ? 0L : filter);
		filterBox.setOnAction(e -> {
			var v = filterBox.getValue();
			filter = v == null || v == 0L ? null : v;
			render();
		});

		var list = new VBox(0);
		var q = query.toLowerCase(Ui.TR);
		var shown = d.transactions().stream().filter(t -> {
			boolean payment = "PAYMENT".equals(t.kind());
			if (filter != null) {
				if (filter == NONE && (payment || t.categoryId() != null)) {
					return false;
				}
				if (filter == -3L && !payment) {
					return false;
				}
				if (filter > 0 && !filter.equals(t.categoryId())) {
					return false;
				}
			}
			return q.isEmpty() || (t.merchant() + " " + t.description()).toLowerCase(Ui.TR).contains(q);
		}).toList();
		if (shown.isEmpty()) {
			list.getChildren().add(Ui.empty("Bu filtrede işlem yok"));
		}
		for (var t : shown) {
			list.getChildren().add(txRow(t, catById));
			if (t.id().equals(editingTx)) {
				list.getChildren().add(editor(t));
			}
		}
		return Ui.card(Ui.row(Ui.label("İşlemler", "h2"), Ui.spacer(), search, filterBox), list);
	}

	private TextField findSearch() {
		return content.lookupAll(".tx-search").stream().filter(TextField.class::isInstance).map(TextField.class::cast).findFirst().orElse(null);
	}

	private Node txRow(StatementTx t, Map<Long, SpendingCategory> catById) {
		boolean payment = "PAYMENT".equals(t.kind());
		var info = new ArrayList<String>();
		if (t.installmentNo() != null) {
			info.add(t.installmentNo() + "/" + t.installmentCount() + " taksit");
		}
		if (t.foreignCurrency() != null) {
			info.add(Money.num(t.foreignAmount()) + " " + t.foreignCurrency());
		}
		if ("FEE".equals(t.kind())) {
			info.add("faiz/ücret");
		}
		if ("REFUND".equals(t.kind())) {
			info.add("iade");
		}
		if ("RULE".equals(t.categorySource()) && t.rulePattern() != null) {
			info.add("kural: \"" + t.rulePattern() + "\"");
		}
		else if ("MANUAL".equals(t.categorySource())) {
			info.add("elle seçildi");
		}
		var merchant = new VBox(1, Ui.label(t.merchant()), Ui.label(String.join(" · ", info), "muted"));
		Tooltip.install(merchant, new Tooltip(t.description()));
		HBox.setHgrow(merchant, Priority.ALWAYS);
		merchant.setMaxWidth(Double.MAX_VALUE);

		var date = Ui.label(t.date().format(Ui.SHORT));
		date.setMinWidth(56);
		var amount = Ui.label(Money.money(t.amount(), "TRY"), t.amount().signum() < 0 ? "pos" : null);
		amount.setMinWidth(100);
		amount.setAlignment(Pos.CENTER_RIGHT);

		Node category;
		if (payment) {
			category = Ui.label("Ödeme", "muted");
		}
		else {
			var combo = categoryCombo(t.id().equals(editingTx) ? editingCategory : t.categoryId());
			var cat = t.categoryId() != null ? catById.get(t.categoryId()) : null;
			combo.setOnAction(e -> onCategoryPicked(t, combo.getValue()));
			category = Ui.row(dot(cat != null ? cat.color() : "#334155"), combo);
		}
		var row = Ui.row(date, merchant, amount, category);
		row.getStyleClass().add("item");
		row.setSpacing(12);
		if (t.id().equals(editingTx)) {
			row.getStyleClass().add("selected-row");
		}
		return row;
	}

	private ComboBox<Long> categoryCombo(Long selected) {
		var combo = new ComboBox<Long>();
		combo.getItems().add(NONE);
		categories.forEach(c -> combo.getItems().add(c.id()));
		combo.getItems().add(NEW);
		var byId = categories.stream().collect(Collectors.toMap(SpendingCategory::id, SpendingCategory::name));
		combo.setConverter(TaskWidgets.converter(v -> v == null || v == NONE ? "— Sınıflandırılmamış —" : v == NEW ? "+ Yeni sınıf…"
				: byId.getOrDefault(v, "?")));
		combo.setValue(selected == null ? NONE : selected);
		combo.setPrefWidth(200);
		return combo;
	}

	/** Sınıf seçilince satırın altında "bundan sonra … geçenler de" seçeneğiyle onay satırı açılır. */
	private void onCategoryPicked(StatementTx t, Long value) {
		if (value == null) {
			return;
		}
		if (value == NEW) {
			var dialog = new TextInputDialog();
			dialog.setTitle("Yeni sınıf");
			dialog.setHeaderText(null);
			dialog.setContentText("Sınıfın adı");
			dialog.getDialogPane().getStylesheets().addAll(getScene().getStylesheets());
			var name = dialog.showAndWait().map(String::trim).filter(n -> !n.isEmpty());
			if (name.isEmpty()) {
				render(); // seçici eski değerine dönsün
				return;
			}
			Ui.async(() -> api.createSpendingCategory(name.get(), NEW_COLORS[categories.size() % NEW_COLORS.length]), c -> {
				var list = new ArrayList<>(categories);
				list.add(c);
				categories = list;
				startEditing(t, c.id());
			});
			return;
		}
		startEditing(t, value == NONE ? null : value);
	}

	private void startEditing(StatementTx t, Long categoryId) {
		editingTx = t.id();
		editingCategory = categoryId;
		render();
	}

	private Node editor(StatementTx t) {
		var remember = new CheckBox("Bundan sonra açıklamasında");
		remember.setSelected(editingCategory != null);
		var pattern = new TextField(t.rulePattern() != null ? t.rulePattern() : t.suggestedPattern());
		pattern.setPrefWidth(180);
		pattern.disableProperty().bind(remember.selectedProperty().not());
		var editor = Ui.row();
		if (editingCategory != null) {
			editor.getChildren().addAll(remember, pattern, Ui.label("geçen işlemler de bu sınıfa girsin"));
		}
		else {
			editor.getChildren().add(Ui.label("İşlemin sınıfı kaldırılacak"));
		}
		var categoryId = editingCategory;
		editor.getChildren().addAll(Ui.spacer(),
				Ui.button("Kaydet", () -> Ui.run(() -> api.setTransactionCategory(t.id(), categoryId,
						remember.isSelected() && categoryId != null, pattern.getText()), () -> {
							editingTx = null;
							editingCategory = null;
							loadDetail(detail.summary().id());
						}), "primary"),
				Ui.button("Vazgeç", () -> {
					editingTx = null;
					editingCategory = null;
					render();
				}));
		editor.getStyleClass().add("edit-row");
		editor.setPadding(new Insets(8, 10, 8, 10));
		return editor;
	}

	private VBox rulesCard() {
		var toggle = new Button((rulesOpen ? "▾" : "▸") + " Sınıflandırma kuralların");
		toggle.getStyleClass().add("link");
		toggle.setOnAction(e -> {
			rulesOpen = !rulesOpen;
			render();
		});
		var card = Ui.card(toggle);
		if (!rulesOpen) {
			return card;
		}
		card.getChildren().add(wrap("Bir işlemin sınıfını değiştirip \"bundan sonra\" dediğinde oluşan kurallar. Hazır kurallardan önce uygulanır.", "muted"));
		var list = new VBox(0);
		card.getChildren().add(list);
		Ui.async(api::categoryRules, rules -> {
			if (rules.isEmpty()) {
				list.getChildren().add(Ui.empty("Henüz kuralın yok"));
			}
			rules.forEach(r -> {
				var combo = categoryCombo(r.categoryId());
				combo.getItems().removeAll(NONE, NEW);
				combo.setOnAction(e -> Ui.run(() -> api.updateCategoryRule(r.id(), r.pattern(), combo.getValue()),
						() -> loadDetail(detail.summary().id())));
				var row = Ui.row(Ui.label("Açıklamasında"), Ui.label(r.pattern(), "code"), Ui.label("geçen"), Ui.spacer(), combo,
						Ui.label(r.matches() + " işlem", "muted"),
						Ui.button("✕", () -> {
							if (Ui.confirm("\"" + r.pattern() + "\" kuralı silinsin mi?")) {
								Ui.run(() -> api.deleteCategoryRule(r.id()), () -> loadDetail(detail.summary().id()));
							}
						}, "ghost", "del"));
				row.getStyleClass().add("item");
				list.getChildren().add(row);
			});
		});
		return card;
	}

	private static Region dot(String color) {
		var r = new Region();
		r.setMinSize(10, 10);
		r.setMaxSize(10, 10);
		r.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 3;");
		return r;
	}

	private static Label wrap(String text, String styleClass) {
		var l = styleClass != null ? Ui.label(text, styleClass) : Ui.label(text);
		l.setWrapText(true);
		l.setMaxWidth(Double.MAX_VALUE);
		return l;
	}
}
