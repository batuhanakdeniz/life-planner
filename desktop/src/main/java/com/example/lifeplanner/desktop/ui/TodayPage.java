package com.example.lifeplanner.desktop.ui;

import static com.example.lifeplanner.desktop.api.ApiClient.body;

import java.time.LocalDate;

import com.example.lifeplanner.desktop.api.ApiClient;
import com.example.lifeplanner.desktop.api.Models.Dashboard;
import com.example.lifeplanner.desktop.api.Models.Habit;
import com.example.lifeplanner.desktop.api.Models.JournalEntry;

import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class TodayPage extends Page {

	private final ApiClient api;
	private LocalDate date = LocalDate.now();

	public TodayPage(ApiClient api) {
		this.api = api;
	}

	@Override
	public void refresh() {
		Ui.async(() -> api.dashboard(date), this::render);
	}

	private void render(Dashboard d) {
		getChildren().clear();
		boolean isToday = date.equals(LocalDate.now());

		var title = new VBox(2, Ui.label(isToday ? "Bugün" : date.format(Ui.DAY_MONTH), "h1"),
				Ui.label(Ui.cap(date.format(Ui.LONG)), "subtitle"));
		var header = Ui.row(title, Ui.spacer(), Ui.button("‹", () -> go(date.minusDays(1))));
		if (!isToday) {
			header.getChildren().add(Ui.button("Bugün", () -> go(LocalDate.now())));
		}
		header.getChildren().add(Ui.button("›", () -> go(date.plusDays(1))));

		var s = d.summary();
		var mood = d.journal() != null && d.journal().mood() != null ? Ui.MOODS[d.journal().mood() - 1] : "—";
		var stats = new HBox(12,
				stat(s.tasksDone() + "/" + s.tasksTotal(), "Görev tamamlandı", false),
				stat(s.habitsDone() + "/" + s.habitsScheduled(), "Alışkanlık yapıldı", false),
				stat(String.valueOf(d.activeGoals().size()), "Aktif hedef", false),
				stat(mood, "Ruh hali", true));

		var grid = new GridPane();
		grid.setHgap(16);
		grid.setVgap(16);
		for (int i = 0; i < 2; i++) {
			var c = new ColumnConstraints();
			c.setPercentWidth(50);
			grid.getColumnConstraints().add(c);
		}
		grid.add(tasksCard(d), 0, 0);
		grid.add(habitsCard(d), 1, 0);
		grid.add(goalsCard(d), 0, 1);
		grid.add(journalCard(d.journal()), 1, 1);
		grid.getChildren().forEach(n -> ((Region) n).setMaxHeight(Double.MAX_VALUE));

		getChildren().addAll(header, stats);
		if (d.duePayments() != null && !d.duePayments().isEmpty()) {
			var due = Ui.card(Ui.label("💳 Yaklaşan ödemeler", "h2"));
			due.getStyleClass().add("due-card");
			d.duePayments().forEach(p -> due.getChildren().add(FinancePage.paymentRow(api, p, this::refresh, true, null)));
			getChildren().add(due);
		}
		getChildren().add(grid);
	}

	private void go(LocalDate d) {
		date = d;
		refresh();
	}

	private VBox stat(String value, String label, boolean plain) {
		var v = Ui.label(value, "stat-value");
		if (plain) {
			v.getStyleClass().add("plain");
		}
		var card = Ui.card(v, Ui.label(label, "stat-label"));
		card.setSpacing(2);
		HBox.setHgrow(card, Priority.ALWAYS);
		card.setMaxWidth(Double.MAX_VALUE);
		return card;
	}

	private VBox tasksCard(Dashboard d) {
		var card = Ui.card(Ui.label("✓ Görevler", "h2"),
				TaskWidgets.quickAdd("Yeni görev ekle… (Enter)", t -> Ui.run(() -> api.createTask(body("title", t, "dueDate", date)), this::refresh)));
		if (d.tasks().isEmpty()) {
			card.getChildren().add(Ui.empty("Bu gün için görev yok 🎉"));
		}
		d.tasks().forEach(t -> card.getChildren().add(TaskWidgets.taskRow(api, t, this::refresh, false, false)));
		return card;
	}

	private VBox habitsCard(Dashboard d) {
		var card = Ui.card(Ui.label("🔁 Alışkanlıklar", "h2"));
		var scheduled = d.habits().stream().filter(Habit::scheduled).toList();
		if (scheduled.isEmpty()) {
			card.getChildren().add(Ui.empty("Bugüne planlı alışkanlık yok"));
		}
		scheduled.forEach(h -> card.getChildren().add(habitRow(h)));
		return card;
	}

	static HBox habitRowBase(ApiClient api, Habit h, LocalDate date, Runnable reload) {
		var btn = new Button(h.icon() != null ? h.icon() : "•");
		btn.getStyleClass().add("habit-btn");
		if (h.doneOnDate()) {
			btn.getStyleClass().add("done");
		}
		btn.setOnAction(e -> Ui.run(() -> api.setHabitDone(h.id(), date, !h.doneOnDate()), reload));

		var dots = new HBox(4);
		h.last7Days().forEach(day -> {
			var dot = new Region();
			dot.getStyleClass().add("dot");
			if (day.done()) {
				dot.getStyleClass().add("done");
			}
			if (!day.scheduled()) {
				dot.getStyleClass().add("off");
			}
			dots.getChildren().add(dot);
		});
		var info = new VBox(4, Ui.label(h.name()), dots);
		HBox.setHgrow(info, Priority.ALWAYS);

		var row = Ui.row(btn, info);
		row.getStyleClass().add("item");
		row.setSpacing(12);
		if (h.color() != null) {
			row.setStyle("-hc: " + h.color() + ";");
		}
		if (h.currentStreak() > 0) {
			row.getChildren().add(Ui.label("🔥 " + h.currentStreak(), "streak"));
		}
		return row;
	}

	private HBox habitRow(Habit h) {
		return habitRowBase(api, h, date, this::refresh);
	}

	private VBox goalsCard(Dashboard d) {
		var card = Ui.card(Ui.label("🎯 Aktif hedefler", "h2"));
		if (d.activeGoals().isEmpty()) {
			card.getChildren().add(Ui.empty("Henüz hedef yok"));
		}
		d.activeGoals().forEach(g -> {
			var top = Ui.row(Ui.label(g.title()), Ui.spacer(), Ui.label("%" + g.progress(), "muted"));
			card.getChildren().add(new VBox(6, top, Ui.progress(g.progress())));
		});
		return card;
	}

	private VBox journalCard(JournalEntry entry) {
		var text = new TextArea(entry != null && entry.content() != null ? entry.content() : "");
		text.setPromptText("Bugün nasıl geçti?");
		text.setWrapText(true);
		text.setPrefRowCount(4);
		Integer[] mood = { entry != null ? entry.mood() : null };

		var moods = new HBox(4);
		for (int i = 0; i < Ui.MOODS.length; i++) {
			int value = i + 1;
			var b = Ui.button(Ui.MOODS[i], () -> {
				mood[0] = value;
				Ui.run(() -> api.saveJournal(date, text.getText(), value), this::refresh);
			}, "mood-btn");
			if (mood[0] != null && mood[0] == value) {
				b.getStyleClass().add("on");
			}
			moods.getChildren().add(b);
		}
		String original = text.getText();
		text.focusedProperty().addListener((obs, was, focused) -> {
			if (!focused && !text.getText().equals(original)) {
				Ui.run(() -> api.saveJournal(date, text.getText(), mood[0]), this::refresh);
			}
		});
		return Ui.card(Ui.label("📓 Günün notu", "h2"), moods, text, Ui.label("Alandan çıkınca otomatik kaydedilir.", "muted"));
	}
}
