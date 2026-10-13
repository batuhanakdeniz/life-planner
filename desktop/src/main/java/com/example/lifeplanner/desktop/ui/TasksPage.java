package com.example.lifeplanner.desktop.ui;

import static com.example.lifeplanner.desktop.api.ApiClient.body;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Predicate;

import com.example.lifeplanner.desktop.api.ApiClient;
import com.example.lifeplanner.desktop.api.Models.Task;

import javafx.scene.Node;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class TasksPage extends Page {

	private enum View { WEEK, LIST, BOARD }

	private final ApiClient api;
	private final VBox body = new VBox(14);
	private View view = View.WEEK;
	private LocalDate monday = Ui.startOfWeek(LocalDate.now());

	public TasksPage(ApiClient api) {
		this.api = api;
		var group = new ToggleGroup();
		var bar = new HBox();
		bar.getStyleClass().add("toggle-group-bar");
		for (var v : View.values()) {
			var b = new ToggleButton(switch (v) {
				case WEEK -> "📅 Haftalık";
				case LIST -> "☰ Liste";
				case BOARD -> "▦ Kanban";
			});
			b.getStyleClass().add("segment");
			b.setToggleGroup(group);
			b.setSelected(v == view);
			b.setOnAction(e -> {
				b.setSelected(true); // seçim kaldırılamasın
				view = v;
				refresh();
			});
			bar.getChildren().add(b);
		}
		getChildren().addAll(
				Ui.row(Ui.label("✓ Görevler", "h1"), Ui.spacer(), bar),
				Ui.label("Görevlerini gün, grup ya da durum bazında yönet.", "subtitle"),
				body);
	}

	@Override
	public void refresh() {
		switch (view) {
			case WEEK -> Ui.async(() -> new Object[] { api.tasksInRange(monday, monday.plusDays(6)), api.somedayTasks() },
					r -> renderWeek(cast(r[0]), cast(r[1])));
			case LIST -> {
				var today = LocalDate.now();
				Ui.async(() -> new Object[] { api.tasksForDay(today), api.tasksInRange(today.plusDays(1), today.plusDays(365)), api.somedayTasks() },
						r -> renderList(cast(r[0]), cast(r[1]), cast(r[2])));
			}
			case BOARD -> Ui.async(api::taskBoard, this::renderBoard);
		}
	}

	@SuppressWarnings("unchecked")
	private static List<Task> cast(Object o) {
		return (List<Task>) o;
	}

	// ---------- Haftalık ----------
	private void renderWeek(List<Task> week, List<Task> someday) {
		var today = LocalDate.now();
		var nav = Ui.row(Ui.button("‹", () -> { monday = monday.minusDays(7); refresh(); }),
				Ui.label(monday.format(Ui.DAY_MONTH) + " – " + monday.plusDays(6).format(Ui.DAY_MONTH), "h2"),
				Ui.button("›", () -> { monday = monday.plusDays(7); refresh(); }));
		if (!monday.equals(Ui.startOfWeek(today))) {
			nav.getChildren().add(Ui.button("Bu hafta", () -> { monday = Ui.startOfWeek(today); refresh(); }));
		}

		var days = new HBox(8);
		for (int i = 0; i < 7; i++) {
			var d = monday.plusDays(i);
			var col = new VBox(Ui.row(Ui.label(Ui.cap(d.format(Ui.WEEKDAY)), d.equals(today) ? "today-col" : "muted"), Ui.spacer(),
					Ui.label(String.valueOf(d.getDayOfMonth()), "day-num")));
			col.getStyleClass().add("day-col");
			if (d.equals(today)) {
				col.getStyleClass().add("today");
			}
			col.setMinHeight(200);
			col.setPrefWidth(120);
			HBox.setHgrow(col, Priority.ALWAYS);
			week.stream().filter(t -> d.equals(t.dueDate()))
					.forEach(t -> col.getChildren().add(TaskWidgets.taskRow(api, t, this::refresh, true, false)));
			col.getChildren().addAll(Ui.spacer(), TaskWidgets.quickAdd("+ Ekle",
					title -> Ui.run(() -> api.createTask(body("title", title, "dueDate", d)), this::refresh)));
			days.getChildren().add(col);
		}

		var somedayCard = Ui.card(Ui.label("☁️ Bir gün", "h2"), Ui.label("Tarihi olmayan görevler", "muted"));
		if (someday.isEmpty()) {
			somedayCard.getChildren().add(Ui.empty("Boş"));
		}
		someday.forEach(t -> somedayCard.getChildren().add(TaskWidgets.taskRow(api, t, this::refresh, false, false)));

		body.getChildren().setAll(TaskWidgets.taskForm(api, null, this::refresh), nav, days, somedayCard);
	}

	// ---------- Gruplu liste ----------
	private void renderList(List<Task> day, List<Task> upcoming, List<Task> someday) {
		var today = LocalDate.now();
		var tomorrow = today.plusDays(1);
		var weekEnd = today.plusDays(7);
		body.getChildren().setAll(TaskWidgets.taskForm(api, today, this::refresh));
		group("⚠️ Gecikmiş", day, Task::overdue, true, false);
		group("☀️ Bugün", day, t -> today.equals(t.dueDate()), false, true);
		group("→ Yarın", upcoming, t -> tomorrow.equals(t.dueDate()), false, false);
		group("📅 Bu hafta", upcoming, t -> t.dueDate().isAfter(tomorrow) && !t.dueDate().isAfter(weekEnd), true, false);
		group("🗓 İleride", upcoming, t -> t.dueDate().isAfter(weekEnd), true, false);
		group("☁️ Bir gün", someday, t -> true, false, false);
	}

	private void group(String title, List<Task> source, Predicate<Task> filter, boolean showDate, boolean alwaysShow) {
		var tasks = source.stream().filter(filter).toList();
		if (tasks.isEmpty() && !alwaysShow) {
			return;
		}
		var card = Ui.card(Ui.row(Ui.label(title, "h2"), Ui.label("(" + tasks.size() + ")", "muted")));
		if (tasks.isEmpty()) {
			card.getChildren().add(Ui.empty("Bugün için görev yok"));
		}
		tasks.forEach(t -> card.getChildren().add(TaskWidgets.taskRow(api, t, this::refresh, false, showDate)));
		body.getChildren().add(card);
	}

	// ---------- Kanban ----------
	private void renderBoard(List<Task> tasks) {
		var board = new HBox(14);
		Ui.TASK_STATUS.forEach((status, label) -> {
			var col = tasks.stream().filter(t -> status.equals(t.status())).toList();
			var head = Ui.row(Ui.label(label, "h2", "col-" + status), Ui.label(String.valueOf(col.size()), "muted"));
			var column = new VBox(head);
			column.getStyleClass().add("board-col");
			column.setMinHeight(320);
			column.setPrefWidth(260);
			HBox.setHgrow(column, Priority.ALWAYS);
			col.forEach(t -> column.getChildren().add(kanbanCard(t)));
			if (col.isEmpty()) {
				column.getChildren().add(Ui.empty("Buraya sürükle"));
			}

			column.setOnDragOver(e -> {
				if (e.getDragboard().hasString()) {
					e.acceptTransferModes(TransferMode.MOVE);
					if (!column.getStyleClass().contains("over")) {
						column.getStyleClass().add("over");
					}
				}
				e.consume();
			});
			column.setOnDragExited(e -> column.getStyleClass().remove("over"));
			column.setOnDragDropped(e -> {
				long id = Long.parseLong(e.getDragboard().getString());
				var task = tasks.stream().filter(t -> t.id() == id).findFirst().orElse(null);
				e.setDropCompleted(true);
				e.consume();
				if (task != null && !status.equals(task.status())) {
					Ui.run(() -> api.setTaskStatus(id, status), this::refresh);
				}
			});
			board.getChildren().add(column);
		});
		body.getChildren().setAll(TaskWidgets.taskForm(api, null, this::refresh),
				Ui.label("Kartları sürükleyerek sütunlar arasında taşı. \"Bitti\" sütununda son 7 gün görünür.", "muted"), board);
	}

	private Node kanbanCard(Task t) {
		var title = Ui.label(t.title(), "title");
		title.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(title, Priority.ALWAYS);
		var del = Ui.button("✕", () -> {
			if (Ui.confirm("\"" + t.title() + "\" silinsin mi?")) {
				Ui.run(() -> api.deleteTask(t.id()), this::refresh);
			}
		}, "ghost", "del");
		var tags = Ui.row();
		if (t.dueDate() != null) {
			tags.getChildren().add(Ui.tag("📅 " + t.dueDate().format(Ui.SHORT), t.overdue() ? "overdue" : null));
		}
		if (!"MEDIUM".equals(t.priority())) {
			tags.getChildren().add(Ui.tag(Ui.PRIORITY.get(t.priority()), t.priority()));
		}
		if (t.goalTitle() != null) {
			tags.getChildren().add(Ui.tag("🎯 " + t.goalTitle(), "goal"));
		}
		var card = new VBox(Ui.row(title, del), tags);
		card.getStyleClass().add("kcard");
		if (t.completed()) {
			card.getStyleClass().add("done");
		}
		card.setOnDragDetected(e -> {
			var db = card.startDragAndDrop(TransferMode.MOVE);
			var content = new ClipboardContent();
			content.putString(String.valueOf(t.id()));
			db.setContent(content);
			db.setDragView(card.snapshot(null, null));
			e.consume();
		});
		return card;
	}
}
