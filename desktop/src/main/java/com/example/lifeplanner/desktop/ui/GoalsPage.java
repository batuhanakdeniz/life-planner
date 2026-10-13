package com.example.lifeplanner.desktop.ui;

import static com.example.lifeplanner.desktop.api.ApiClient.body;
import static java.time.temporal.ChronoUnit.DAYS;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.example.lifeplanner.desktop.api.ApiClient;
import com.example.lifeplanner.desktop.api.Models.Goal;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

public class GoalsPage extends Page {

	private static final double LABEL_WIDTH = 220;
	private static final double END_LABEL_SPACE = 160;

	private final ApiClient api;
	private final VBox body = new VBox(16);
	private String filter = "ACTIVE";
	private Long selected;

	public GoalsPage(ApiClient api) {
		this.api = api;
		var group = new ToggleGroup();
		var bar = new HBox();
		bar.getStyleClass().add("toggle-group-bar");
		for (var f : List.of(Map.entry("ACTIVE", "Aktif"), Map.entry("COMPLETED", "Tamamlanan"), Map.entry("ALL", "Tümü"))) {
			var b = new ToggleButton(f.getValue());
			b.getStyleClass().add("segment");
			b.setToggleGroup(group);
			b.setSelected(f.getKey().equals(filter));
			b.setOnAction(e -> {
				b.setSelected(true);
				filter = f.getKey();
				refresh();
			});
			bar.getChildren().add(b);
		}
		getChildren().addAll(
				Ui.row(Ui.label("🎯 Hedefler", "h1"), Ui.spacer(), bar, Ui.button("+ Yeni hedef", () -> openForm(null), "primary")),
				Ui.label("Hedeflerin, hedef tarihlerine göre zaman çizelgesinde.", "subtitle"),
				body);
	}

	@Override
	public void refresh() {
		Ui.async(() -> api.goals("ALL".equals(filter) ? null : filter), this::render);
	}

	private void render(List<Goal> goals) {
		var dated = goals.stream().filter(g -> g.targetDate() != null).sorted(Comparator.comparing(Goal::targetDate)).toList();
		var undated = goals.stream().filter(g -> g.targetDate() == null).toList();

		body.getChildren().setAll(Ui.card(dated.isEmpty()
				? Ui.empty("Tarihli hedef yok. Hedefe bir tarih verirsen burada görünür.")
				: timeline(dated)));

		if (!undated.isEmpty()) {
			var card = Ui.card(Ui.label("Tarihsiz hedefler", "h2"));
			undated.forEach(g -> {
				var bar = Ui.progress(g.progress());
				bar.setPrefWidth(200);
				var row = Ui.row(Ui.label(Ui.CATEGORY_ICON.get(g.category()) + " " + g.title()), Ui.spacer(), bar, Ui.label("%" + g.progress(), "muted"));
				row.getStyleClass().add("tl-row");
				row.setPadding(new Insets(8));
				if (g.id().equals(selected)) {
					row.getStyleClass().add("selected");
				}
				row.setOnMouseClicked(e -> select(g));
				card.getChildren().add(row);
			});
			body.getChildren().add(card);
		}
		goals.stream().filter(g -> g.id().equals(selected)).findFirst().ifPresent(g -> body.getChildren().add(detail(g)));
	}

	private void select(Goal g) {
		selected = g.id().equals(selected) ? null : g.id();
		refresh();
	}

	// ---------- Zaman çizelgesi ----------
	private Node timeline(List<Goal> goals) {
		var now = LocalDate.now();
		var min = goals.stream().map(Goal::startDate).filter(d -> d.isBefore(now)).min(LocalDate::compareTo).orElse(now);
		var last = goals.get(goals.size() - 1).targetDate();
		var max = (last.isAfter(now) ? last : now).plusDays(30);
		double span = Math.max(DAYS.between(min, max), 1);

		List<LocalDate> months = new ArrayList<>();
		for (var m = min.withDayOfMonth(1).plusMonths(1); !m.isAfter(max); m = m.plusMonths(1)) {
			months.add(m);
		}
		int step = months.size() > 24 ? 6 : months.size() > 12 ? 3 : 1;

		var box = new VBox(2);
		// Eksen: ay etiketleri (ilk etikette ve yıl değişince yıl da yazılır)
		var axis = new Track(span, min, months, now, false);
		Integer prevYear = null;
		for (int i = 0; i < months.size(); i += step) {
			var m = months.get(i);
			boolean newYear = prevYear == null || prevYear != m.getYear();
			prevYear = m.getYear();
			axis.addAt(Ui.label(Ui.cap(m.format(java.time.format.DateTimeFormatter.ofPattern(newYear ? "MMM yyyy" : "MMM", Ui.TR))), "tl-month"),
					DAYS.between(min, m), true);
		}
		axis.setPrefHeight(22);
		box.getChildren().add(rowOf(new Label(), axis, null));

		for (var g : goals) {
			var track = new Track(span, min, months, now, true);
			long startOff = DAYS.between(min, g.startDate());
			long len = Math.max(DAYS.between(g.startDate(), g.targetDate()), 1);
			long daysLeft = DAYS.between(now, g.targetDate());
			boolean late = daysLeft < 0 && "ACTIVE".equals(g.status());
			track.setBar(startOff, len, g.progress(), late, "COMPLETED".equals(g.status()));
			track.setEndLabel(Ui.label("%" + g.progress() + " · " + ("COMPLETED".equals(g.status()) ? "✓ Tamamlandı"
					: late ? (-daysLeft) + " gün gecikti" : daysLeft + " gün kaldı"), "tl-end"));
			var label = Ui.label(Ui.CATEGORY_ICON.get(g.category()) + "  " + g.title());
			box.getChildren().add(rowOf(label, track, g));
		}
		var nowLabel = new Track(span, min, months, now, false);
		nowLabel.addAt(Ui.label("Bugün", "today-col"), DAYS.between(min, now), true);
		nowLabel.setPrefHeight(18);
		box.getChildren().add(rowOf(new Label(), nowLabel, null));
		return box;
	}

	private HBox rowOf(Label label, Track track, Goal g) {
		label.setMinWidth(LABEL_WIDTH);
		label.setMaxWidth(LABEL_WIDTH);
		label.setPadding(new Insets(0, 10, 0, 10));
		HBox.setHgrow(track, Priority.ALWAYS);
		var row = new HBox(label, track);
		row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
		if (g != null) {
			row.getStyleClass().add("tl-row");
			row.setMinHeight(44);
			if (g.id().equals(selected)) {
				row.getStyleClass().add("selected");
			}
			row.setOnMouseClicked(e -> select(g));
		}
		return row;
	}

	/** Gün ofsetlerini genişliğe oranlayarak çocukları yerleştiren çizelge şeridi. */
	private static final class Track extends Pane {

		private final double span;
		private final List<Node> positioned = new ArrayList<>();
		private final List<Double> offsets = new ArrayList<>();
		private final List<Boolean> centered = new ArrayList<>();
		private Rectangle bar;
		private Rectangle fill;
		private double barStart;
		private double barLength;
		private double progress;
		private Label endLabel;

		Track(double span, LocalDate min, List<LocalDate> months, LocalDate now, boolean withGrid) {
			this.span = span;
			setMinWidth(300);
			if (withGrid) {
				for (var m : months) {
					var line = new Rectangle(1, 1);
					line.getStyleClass().add("tl-grid");
					addAt(line, DAYS.between(min, m), false);
				}
				var nowLine = new Rectangle(2, 1);
				nowLine.getStyleClass().add("tl-now");
				addAt(nowLine, DAYS.between(min, now), false);
			}
		}

		void addAt(Node node, double dayOffset, boolean center) {
			positioned.add(node);
			offsets.add(dayOffset);
			centered.add(center);
			getChildren().add(node);
		}

		void setBar(double start, double length, int progress, boolean late, boolean completed) {
			this.barStart = start;
			this.barLength = length;
			this.progress = progress;
			bar = new Rectangle();
			bar.getStyleClass().add("tl-bar");
			if (late) {
				bar.getStyleClass().add("late");
			}
			bar.setArcWidth(16);
			bar.setArcHeight(16);
			fill = new Rectangle();
			fill.getStyleClass().add("tl-fill");
			if (completed) {
				fill.getStyleClass().add("completed");
			}
			fill.setArcWidth(16);
			fill.setArcHeight(16);
			getChildren().addAll(bar, fill);
		}

		void setEndLabel(Label label) {
			endLabel = label;
			getChildren().add(label);
		}

		@Override
		protected void layoutChildren() {
			double w = Math.max(getWidth() - END_LABEL_SPACE, 1);
			double h = getHeight();
			for (int i = 0; i < positioned.size(); i++) {
				var n = positioned.get(i);
				double x = offsets.get(i) / span * w;
				if (n instanceof Rectangle r) {
					r.setX(x);
					r.setY(0);
					r.setHeight(h);
				}
				else {
					n.autosize();
					double nw = n.getLayoutBounds().getWidth();
					n.relocate(centered.get(i) ? Math.max(0, x - nw / 2) : x, (h - n.getLayoutBounds().getHeight()) / 2);
				}
			}
			if (bar != null) {
				double x = barStart / span * w;
				double bw = Math.max(barLength / span * w, 6);
				bar.setX(x);
				bar.setY(h / 2 - 8);
				bar.setWidth(bw);
				bar.setHeight(16);
				fill.setX(x);
				fill.setY(h / 2 - 8);
				fill.setWidth(bw * progress / 100.0);
				fill.setHeight(16);
				if (endLabel != null) {
					endLabel.autosize();
					endLabel.relocate(x + bw + 8, (h - endLabel.getLayoutBounds().getHeight()) / 2);
				}
			}
		}
	}

	// ---------- Detay ----------
	private VBox detail(Goal g) {
		var header = Ui.row(Ui.label(Ui.CATEGORY_ICON.get(g.category()) + " " + g.title(), "h2"),
				Ui.tag(Ui.CATEGORY.get(g.category()), null), Ui.tag(Ui.GOAL_STATUS.get(g.status()), "goal"), Ui.spacer(),
				Ui.button("Düzenle", () -> openForm(g)),
				Ui.button("Sil", () -> {
					if (Ui.confirm("\"" + g.title() + "\" silinsin mi? Bağlı görevler silinmez.")) {
						selected = null;
						Ui.run(() -> api.deleteGoal(g.id()), this::refresh);
					}
				}, "danger"));
		var fmt = java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", Ui.TR);
		var dates = g.startDate().format(fmt) + " başladı" + (g.targetDate() != null ? " · Hedef: " + g.targetDate().format(fmt) : "");
		var card = Ui.card(header, Ui.label(dates, "muted"));
		if (g.description() != null) {
			var d = Ui.label(g.description());
			d.setWrapText(true);
			card.getChildren().add(d);
		}
		var bar = Ui.progress(g.progress());
		HBox.setHgrow(bar, Priority.ALWAYS);
		card.getChildren().addAll(Ui.row(bar, Ui.label("%" + g.progress(), "h2")), Ui.label("Adımlar", "h2"));
		if (g.milestones().isEmpty()) {
			card.getChildren().add(Ui.empty("Henüz adım yok"));
		}
		g.milestones().forEach(m -> {
			var check = new CheckBox();
			check.setSelected(m.done());
			check.setOnAction(e -> Ui.run(() -> api.setMilestoneDone(m.id(), check.isSelected()), this::refresh));
			var title = Ui.label(m.title(), "title");
			HBox.setHgrow(title, Priority.ALWAYS);
			title.setMaxWidth(Double.MAX_VALUE);
			var row = Ui.row(check, title, Ui.button("✕", () -> Ui.run(() -> api.deleteMilestone(m.id()), this::refresh), "ghost", "del"));
			row.getStyleClass().add("item");
			if (m.done()) {
				row.getStyleClass().add("done");
			}
			card.getChildren().add(row);
		});
		var add = TaskWidgets.quickAdd("Yeni adım ekle… (Enter)", t -> Ui.run(() -> api.addMilestone(g.id(), t), this::refresh));
		add.getStyleClass().remove("quick-add");
		card.getChildren().add(add);
		return card;
	}

	// ---------- Form ----------
	private void openForm(Goal goal) {
		var dialog = new Dialog<Map<String, Object>>();
		dialog.setTitle(goal == null ? "Yeni hedef" : "Hedefi düzenle");
		dialog.setHeaderText(null);
		dialog.getDialogPane().getStylesheets().addAll(getScene().getStylesheets());
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		var title = new TextField(goal != null ? goal.title() : "");
		title.setPromptText("Ör. İngilizce B2 seviyesine ulaş");
		var category = new ComboBox<String>();
		category.getItems().addAll(Ui.CATEGORY.keySet());
		category.setValue(goal != null ? goal.category() : "PERSONAL");
		category.setConverter(TaskWidgets.converter(k -> Ui.CATEGORY_ICON.get(k) + " " + Ui.CATEGORY.get(k)));
		var target = new DatePicker(goal != null ? goal.targetDate() : null);
		target.setPromptText("Hedef tarih");
		var status = new ComboBox<String>();
		status.getItems().addAll(Ui.GOAL_STATUS.keySet());
		status.setValue(goal != null ? goal.status() : "ACTIVE");
		status.setConverter(TaskWidgets.converter(Ui.GOAL_STATUS::get));
		var desc = new TextArea(goal != null && goal.description() != null ? goal.description() : "");
		desc.setPromptText("Neden bu hedef? (isteğe bağlı)");
		desc.setPrefRowCount(3);
		desc.setWrapText(true);
		var steps = new TextArea();
		steps.setPromptText("Adımlar — her satıra bir tane\nA2 kursunu bitir\nB1 sınavına gir");
		steps.setPrefRowCount(4);

		var row = Ui.row(category, target);
		if (goal != null) {
			row.getChildren().add(status);
		}
		var form = new VBox(12, title, row, desc);
		if (goal == null) {
			form.getChildren().add(steps);
		}
		form.setPadding(new Insets(10));
		form.setPrefWidth(520);
		dialog.getDialogPane().setContent(form);
		dialog.setOnShown(e -> title.requestFocus());
		dialog.setResultConverter(bt -> {
			if (bt != ButtonType.OK || title.getText().isBlank()) {
				return null;
			}
			var milestones = goal != null ? null
					: Arrays.stream(steps.getText().split("\n")).map(String::trim).filter(s -> !s.isEmpty()).toList();
			return body("title", title.getText().trim(), "category", category.getValue(), "status", status.getValue(),
					"targetDate", target.getValue(), "description", desc.getText().isBlank() ? null : desc.getText().trim(),
					"milestones", milestones);
		});
		dialog.showAndWait().ifPresent(req -> Ui.run(() -> {
			if (goal == null) {
				api.createGoal(req);
			}
			else {
				api.updateGoal(goal.id(), req);
			}
		}, this::refresh));
	}
}
