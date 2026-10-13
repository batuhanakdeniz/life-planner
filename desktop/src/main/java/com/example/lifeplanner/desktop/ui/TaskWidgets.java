package com.example.lifeplanner.desktop.ui;

import static com.example.lifeplanner.desktop.api.ApiClient.body;

import java.time.LocalDate;
import java.util.function.Consumer;

import com.example.lifeplanner.desktop.api.ApiClient;
import com.example.lifeplanner.desktop.api.Models.Goal;
import com.example.lifeplanner.desktop.api.Models.Task;

import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.StringConverter;

/** Görev satırı ve görev ekleme formu; birden çok sayfada kullanılır. */
public final class TaskWidgets {

	private TaskWidgets() {
	}

	public static HBox taskRow(ApiClient api, Task t, Runnable reload, boolean compact, boolean showDate) {
		var check = new CheckBox();
		check.setSelected(t.completed());
		check.setOnAction(e -> Ui.run(() -> api.setTaskDone(t.id(), check.isSelected()), reload));

		var title = Ui.label(t.title(), "title");
		title.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(title, Priority.ALWAYS);

		var row = Ui.row(check, title);
		row.getStyleClass().add("item");
		if (compact) {
			row.getStyleClass().add("compact");
		}
		if (t.completed()) {
			row.getStyleClass().add("done");
		}
		if (showDate && t.dueDate() != null) {
			row.getChildren().add(Ui.tag(t.dueDate().format(Ui.SHORT), null));
		}
		if (t.overdue()) {
			row.getChildren().add(Ui.tag("Gecikti", "overdue"));
		}
		if (!compact && t.goalTitle() != null) {
			row.getChildren().add(Ui.tag("🎯 " + t.goalTitle(), "goal"));
		}
		Runnable delete = () -> {
			if (Ui.confirm("\"" + t.title() + "\" silinsin mi?")) {
				Ui.run(() -> api.deleteTask(t.id()), reload);
			}
		};
		if (compact) {
			// Dar sütunlarda yer kazanmak için: öncelik başlık rengiyle, silme sağ tık menüsüyle
			title.getStyleClass().add("prio-" + t.priority());
			var deleteItem = new MenuItem("Sil");
			deleteItem.setOnAction(e -> delete.run());
			var menu = new ContextMenu(deleteItem);
			row.setOnContextMenuRequested(e -> menu.show(row, e.getScreenX(), e.getScreenY()));
			return row;
		}
		if (!"MEDIUM".equals(t.priority())) {
			row.getChildren().add(Ui.tag(Ui.PRIORITY.get(t.priority()), t.priority()));
		}
		row.getChildren().add(Ui.button("✕", delete, "ghost", "del"));
		return row;
	}

	/** Başlık, tarih, öncelik ve hedef seçimli görev ekleme satırı. */
	public static HBox taskForm(ApiClient api, LocalDate defaultDate, Runnable reload) {
		var title = new TextField();
		title.setPromptText("Yeni görev…");
		HBox.setHgrow(title, Priority.ALWAYS);

		var date = new DatePicker(defaultDate);
		date.setPromptText("Tarih (boş = bir gün)");
		date.setPrefWidth(150);

		var priority = new ComboBox<String>();
		priority.getItems().addAll(Ui.PRIORITY.keySet());
		priority.setValue("MEDIUM");
		priority.setConverter(converter(Ui.PRIORITY::get));

		var goal = new ComboBox<Goal>();
		goal.setPromptText("Hedef yok");
		goal.setConverter(converter(g -> g == null ? "Hedef yok" : "🎯 " + g.title()));
		Ui.async(() -> api.goals("ACTIVE"), goals -> {
			goal.getItems().add(null);
			goal.getItems().addAll(goals);
		});

		Runnable submit = () -> {
			if (title.getText().isBlank()) {
				return;
			}
			var g = goal.getValue();
			var req = body("title", title.getText().trim(), "dueDate", date.getValue(), "priority", priority.getValue(),
					"goalId", g != null ? g.id() : null);
			title.clear();
			Ui.run(() -> api.createTask(req), reload);
		};
		title.setOnAction(e -> submit.run());

		var row = Ui.row(title, date, priority, goal, Ui.button("Ekle", submit, "primary"));
		row.getStyleClass().add("card");
		return row;
	}

	public static <T> StringConverter<T> converter(java.util.function.Function<T, String> toString) {
		return new StringConverter<>() {
			@Override
			public String toString(T object) {
				return toString.apply(object);
			}

			@Override
			public T fromString(String string) {
				return null;
			}
		};
	}

	/** Enter'a basınca metni teslim edip temizleyen hızlı ekleme alanı. */
	public static TextField quickAdd(String prompt, Consumer<String> onSubmit) {
		var field = new TextField();
		field.setPromptText(prompt);
		field.getStyleClass().add("quick-add");
		field.setOnAction(e -> {
			if (!field.getText().isBlank()) {
				onSubmit.accept(field.getText().trim());
				field.clear();
			}
		});
		return field;
	}
}
