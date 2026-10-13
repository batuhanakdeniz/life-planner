package com.example.lifeplanner.desktop.ui;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.example.lifeplanner.desktop.api.ApiClient;
import com.example.lifeplanner.desktop.api.Models.JournalEntry;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class JournalPage extends Page {

	private static final String[] MOOD_LABELS = { "Çok kötü", "Kötü", "Normal", "İyi", "Harika" };

	private final ApiClient api;
	private final HBox body = new HBox(20);
	private LocalDate month = LocalDate.now().withDayOfMonth(1);
	private LocalDate selected = LocalDate.now();
	private Map<LocalDate, JournalEntry> entries = Map.of();

	public JournalPage(ApiClient api) {
		this.api = api;
		getChildren().addAll(Ui.label("📓 Günlük", "h1"),
				Ui.label("Her gün için kısa bir not ve ruh hali. Renkler o günün ruh halini gösterir.", "subtitle"), body);
	}

	@Override
	public void refresh() {
		Ui.async(() -> api.journal(month, month.plusMonths(1).minusDays(1)), list -> {
			entries = list.stream().collect(Collectors.toMap(JournalEntry::date, Function.identity()));
			render();
		});
	}

	private void render() {
		var today = LocalDate.now();
		var header = Ui.row(Ui.button("‹", () -> { month = month.minusMonths(1); refresh(); }), Ui.spacer(),
				Ui.label(Ui.cap(month.format(Ui.MONTH_YEAR)), "h2"), Ui.spacer(),
				Ui.button("›", () -> { month = month.plusMonths(1); refresh(); }));

		var cal = new GridPane();
		cal.setHgap(4);
		cal.setVgap(4);
		var days = DayOfWeek.values();
		for (int i = 0; i < 7; i++) {
			var l = Ui.label(Ui.dayLabel(days[i]), "muted");
			l.setMaxWidth(Double.MAX_VALUE);
			l.setAlignment(javafx.geometry.Pos.CENTER);
			cal.add(l, i, 0);
		}
		var gridStart = Ui.startOfWeek(month);
		for (int i = 0; i < 42; i++) {
			var d = gridStart.plusDays(i);
			var e = entries.get(d);
			var b = new Button(String.valueOf(d.getDayOfMonth()));
			if (e != null) {
				b.setGraphic(Ui.label(e.mood() != null ? Ui.MOODS[e.mood() - 1] : "•"));
			}
			b.getStyleClass().add("cal-day");
			if (d.getMonthValue() != month.getMonthValue()) {
				b.getStyleClass().add("outside");
			}
			if (d.equals(selected)) {
				b.getStyleClass().add("sel");
			}
			if (d.equals(today)) {
				b.getStyleClass().add("now");
			}
			if (e != null && e.mood() != null) {
				b.getStyleClass().add("mood-" + e.mood());
			}
			b.setDisable(d.isAfter(today));
			b.setOnAction(ev -> {
				selected = d;
				if (d.getMonthValue() != month.getMonthValue()) {
					month = d.withDayOfMonth(1);
					refresh();
				}
				else {
					render();
				}
			});
			cal.add(b, i % 7, i / 7 + 1);
		}

		var moods = entries.values().stream().map(JournalEntry::mood).filter(m -> m != null).toList();
		var footer = Ui.row(Ui.label(entries.size() + " kayıt", "muted"), Ui.spacer());
		if (!moods.isEmpty()) {
			double avg = moods.stream().mapToInt(Integer::intValue).average().orElse(0);
			footer.getChildren().add(Ui.label("Ortalama ruh hali: " + Ui.MOODS[(int) Math.round(avg) - 1] + " " + String.format(Ui.TR, "%.1f", avg), "muted"));
		}
		var calendarCard = Ui.card(header, cal, footer);
		calendarCard.setMinWidth(360);
		calendarCard.setMaxWidth(380);

		var editor = editor(selected, entries.get(selected));
		HBox.setHgrow(editor, Priority.ALWAYS);
		body.getChildren().setAll(calendarCard, editor);
	}

	private VBox editor(LocalDate date, JournalEntry entry) {
		var original = entry != null && entry.content() != null ? entry.content() : "";
		Integer originalMood = entry != null ? entry.mood() : null;
		Integer[] mood = { originalMood };

		var text = new TextArea(original);
		text.setWrapText(true);
		text.setPromptText("Bugün neler oldu? Neye minnettarsın? Yarın için ne düşünüyorsun?");
		text.setPrefRowCount(14);
		VBox.setVgrow(text, Priority.ALWAYS);

		var status = Ui.label("", "muted");
		var save = Ui.button("Kaydet", () -> { }, "primary");
		Runnable updateDirty = () -> {
			boolean dirty = !text.getText().equals(original) || !java.util.Objects.equals(mood[0], originalMood);
			save.setDisable(!dirty);
			status.setText(dirty ? "Kaydedilmemiş değişiklikler · ⌘S" : "");
		};
		save.setOnAction(e -> {
			status.setText("Kaydediliyor…");
			Ui.run(() -> api.saveJournal(date, text.getText(), mood[0]), this::refresh);
		});
		text.textProperty().addListener((o, a, b) -> updateDirty.run());
		text.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.S && e.isShortcutDown() && !save.isDisabled()) {
				save.fire();
				e.consume();
			}
		});

		var moodRow = new HBox(4);
		List<Button> moodButtons = new java.util.ArrayList<>();
		for (int i = 0; i < Ui.MOODS.length; i++) {
			int value = i + 1;
			var b = Ui.button(Ui.MOODS[i], () -> { }, "mood-btn");
			b.setTooltip(new javafx.scene.control.Tooltip(MOOD_LABELS[i]));
			b.setOnAction(e -> {
				mood[0] = java.util.Objects.equals(mood[0], value) ? null : value;
				moodButtons.forEach(mb -> mb.getStyleClass().remove("on"));
				if (mood[0] != null) {
					moodButtons.get(mood[0] - 1).getStyleClass().add("on");
				}
				updateDirty.run();
			});
			if (java.util.Objects.equals(mood[0], value)) {
				b.getStyleClass().add("on");
			}
			moodButtons.add(b);
		}
		moodRow.getChildren().addAll(moodButtons);
		updateDirty.run();

		Label title = Ui.label(Ui.cap(date.format(Ui.FULL)), "h2");
		var card = Ui.card(title, moodRow, text, Ui.row(save, status));
		card.setSpacing(14);
		return card;
	}
}
