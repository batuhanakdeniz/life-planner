package com.example.lifeplanner.desktop.ui;

import static com.example.lifeplanner.desktop.api.ApiClient.body;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.lifeplanner.desktop.api.ApiClient;
import com.example.lifeplanner.desktop.api.Models.Habit;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class HabitsPage extends Page {

	private static final int HEATMAP_WEEKS = 26;

	private final ApiClient api;
	private final VBox body = new VBox(16);
	private final CheckBox showArchived = new CheckBox("Arşivi göster");
	private LocalDate monday = Ui.startOfWeek(LocalDate.now());
	private Long selected;

	public HabitsPage(ApiClient api) {
		this.api = api;
		showArchived.setOnAction(e -> refresh());
		getChildren().addAll(
				Ui.row(Ui.label("🔁 Alışkanlıklar", "h1"), Ui.spacer(), Ui.button("+ Yeni alışkanlık", () -> openForm(null), "primary")),
				Ui.label("Bir güne tıklayarak işaretle; detay ve geçmiş için alışkanlığın adına tıkla.", "subtitle"),
				body);
	}

	@Override
	public void refresh() {
		// Pazar gününü referans verince last7Days tam olarak Pzt–Paz haftasını kapsar
		Ui.async(() -> api.habits(monday.plusDays(6), showArchived.isSelected()), this::render);
	}

	private void render(List<Habit> habits) {
		var today = LocalDate.now();
		var sunday = monday.plusDays(6);
		var next = Ui.button("›", () -> { monday = monday.plusDays(7); refresh(); });
		next.setDisable(!sunday.isBefore(today));
		var nav = Ui.row(Ui.button("‹", () -> { monday = monday.minusDays(7); refresh(); }),
				Ui.label(monday.format(Ui.DAY_MONTH) + " – " + sunday.format(Ui.DAY_MONTH), "h2"), next, Ui.spacer(), showArchived);

		var grid = new GridPane();
		grid.setHgap(6);
		grid.setVgap(4);
		var nameCol = new ColumnConstraints();
		nameCol.setHgrow(Priority.ALWAYS);
		nameCol.setMinWidth(200);
		grid.getColumnConstraints().add(nameCol);
		for (int i = 0; i <= 7; i++) {
			var c = new ColumnConstraints(54);
			c.setHalignment(HPos.CENTER);
			grid.getColumnConstraints().add(c);
		}
		for (int i = 0; i < 7; i++) {
			var d = monday.plusDays(i);
			var head = new VBox(Ui.label(Ui.cap(d.format(Ui.WEEKDAY)), d.equals(today) ? "today-col" : "muted"),
					Ui.label(String.valueOf(d.getDayOfMonth()), "muted"));
			head.setAlignment(Pos.CENTER);
			grid.add(head, i + 1, 0);
		}
		grid.add(Ui.label("Seri", "muted"), 8, 0);

		if (habits.isEmpty()) {
			grid.add(Ui.empty("Henüz alışkanlık yok. \"+ Yeni alışkanlık\" ile başla."), 0, 1, 9, 1);
		}
		int r = 1;
		for (var h : habits) {
			var name = Ui.label((h.icon() != null ? h.icon() + "  " : "• ") + h.name(), "habit-name");
			name.setOnMouseClicked(e -> {
				selected = h.id().equals(selected) ? null : h.id();
				refresh();
			});
			var nameBox = new HBox(name);
			nameBox.setPadding(new Insets(8));
			nameBox.setAlignment(Pos.CENTER_LEFT);
			if (h.id().equals(selected)) {
				nameBox.getStyleClass().add("selected-row");
			}
			if (h.archived()) {
				nameBox.setOpacity(0.5);
			}
			grid.add(nameBox, 0, r);
			for (int i = 0; i < h.last7Days().size(); i++) {
				var d = h.last7Days().get(i);
				if (!d.scheduled()) {
					grid.add(Ui.label("·", "muted"), i + 1, r);
					continue;
				}
				var cell = new Button(d.done() ? "✓" : "");
				cell.getStyleClass().add("cell-btn");
				if (d.done()) {
					cell.getStyleClass().add("done");
				}
				if (h.color() != null) {
					cell.setStyle("-hc: " + h.color() + ";");
				}
				cell.setDisable(d.date().isAfter(today) || h.archived());
				cell.setOnAction(e -> Ui.run(() -> api.setHabitDone(h.id(), d.date(), !d.done()), this::refresh));
				grid.add(cell, i + 1, r);
			}
			grid.add(Ui.label(h.currentStreak() > 0 ? "🔥 " + h.currentStreak() : "—", "streak"), 8, r);
			r++;
		}

		body.getChildren().setAll(Ui.card(nav, grid));
		habits.stream().filter(h -> h.id().equals(selected)).findFirst().ifPresent(h -> body.getChildren().add(detail(h)));
	}

	private VBox detail(Habit h) {
		var card = Ui.card();
		if (h.color() != null) {
			card.setStyle("-hc: " + h.color() + ";");
		}
		var header = Ui.row(Ui.label((h.icon() != null ? h.icon() + " " : "") + h.name(), "h2"), Ui.spacer(),
				Ui.button("Düzenle", () -> openForm(h)),
				Ui.button(h.archived() ? "Arşivden çıkar" : "Arşivle", () -> Ui.run(() -> api.updateHabit(h.id(), toBody(h, !h.archived())), this::refresh)),
				Ui.button("Sil", () -> {
					if (Ui.confirm("\"" + h.name() + "\" ve tüm geçmişi silinsin mi?")) {
						selected = null;
						Ui.run(() -> api.deleteHabit(h.id()), this::refresh);
					}
				}, "danger"));
		card.getChildren().add(header);
		if (h.description() != null) {
			card.getChildren().add(Ui.label(h.description(), "muted"));
		}

		var end = LocalDate.now();
		var start = Ui.startOfWeek(end.minusWeeks(HEATMAP_WEEKS - 1));
		var rate = Ui.label("…", "stat-value");
		card.getChildren().add(new HBox(12, stat("🔥 " + h.currentStreak(), "Mevcut seri"), stat("⭐ " + h.longestStreak(), "En uzun seri"),
				statNode(rate, "Başarı oranı")));

		var heat = new GridPane();
		heat.setHgap(3);
		heat.setVgap(3);
		var days = DayOfWeek.values();
		for (int i = 0; i < 7; i++) {
			heat.add(Ui.label(i % 2 == 0 ? Ui.dayLabel(days[i]) : "", "muted"), 0, i);
		}
		card.getChildren().add(heat);

		Ui.async(() -> api.habitHistory(h.id(), start, end), dates -> {
			Set<LocalDate> done = new HashSet<>(dates);
			int scheduledPast = 0;
			for (int w = 0; w < HEATMAP_WEEKS; w++) {
				for (int d = 0; d < 7; d++) {
					var day = start.plusDays(w * 7L + d);
					if (day.isAfter(end)) {
						continue;
					}
					boolean scheduled = h.days().isEmpty() || h.days().contains(day.getDayOfWeek().name());
					boolean active = !day.isBefore(h.startDate());
					if (scheduled && active) {
						scheduledPast++;
					}
					var cell = new Region();
					cell.getStyleClass().add("hm");
					if (done.contains(day)) {
						cell.getStyleClass().add("done");
					}
					else if (!scheduled || !active) {
						cell.getStyleClass().add("off");
					}
					javafx.scene.control.Tooltip.install(cell, new javafx.scene.control.Tooltip(day.format(Ui.DAY_MONTH) + (done.contains(day) ? " ✓" : "")));
					heat.add(cell, w + 1, d);
				}
			}
			rate.setText("%" + (scheduledPast == 0 ? 0 : Math.round(done.size() * 100.0 / scheduledPast)));
		});
		return card;
	}

	private VBox stat(String value, String label) {
		return statNode(Ui.label(value, "stat-value"), label);
	}

	private VBox statNode(Label value, String label) {
		var c = Ui.card(value, Ui.label(label, "stat-label"));
		c.setSpacing(2);
		HBox.setHgrow(c, Priority.ALWAYS);
		c.setMaxWidth(Double.MAX_VALUE);
		return c;
	}

	private static java.util.Map<String, Object> toBody(Habit h, boolean archived) {
		return body("name", h.name(), "description", h.description(), "icon", h.icon(), "color", h.color(),
				"days", h.days(), "archived", archived);
	}

	private void openForm(Habit habit) {
		var dialog = new Dialog<java.util.Map<String, Object>>();
		dialog.setTitle(habit == null ? "Yeni alışkanlık" : "Alışkanlığı düzenle");
		dialog.setHeaderText(null);
		dialog.getDialogPane().getStylesheets().addAll(getScene().getStylesheets());
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		var icon = new TextField(habit != null && habit.icon() != null ? habit.icon() : "");
		icon.setPromptText("📚");
		icon.setPrefWidth(60);
		var name = new TextField(habit != null ? habit.name() : "");
		name.setPromptText("Ör. 20 sayfa kitap oku");
		HBox.setHgrow(name, Priority.ALWAYS);
		var desc = new TextField(habit != null && habit.description() != null ? habit.description() : "");
		desc.setPromptText("Açıklama (isteğe bağlı)");

		List<ToggleButton> dayButtons = new ArrayList<>();
		var dayRow = Ui.row(Ui.label("Günler:", "muted"));
		for (var d : DayOfWeek.values()) {
			var b = new ToggleButton(Ui.dayLabel(d));
			b.getStyleClass().add("segment");
			b.setUserData(d.name());
			b.setSelected(habit != null && habit.days().contains(d.name()));
			dayButtons.add(b);
			dayRow.getChildren().add(b);
		}
		dayRow.getChildren().add(Ui.label("(seçilmezse her gün)", "muted"));

		String[] color = { habit != null && habit.color() != null ? habit.color() : Ui.HABIT_COLORS[0] };
		var colorRow = Ui.row(Ui.label("Renk:", "muted"));
		List<Button> swatches = new ArrayList<>();
		Runnable paint = () -> swatches.forEach(sw -> {
			var c = (String) sw.getUserData();
			sw.setStyle("-fx-background-color: " + c + "; -fx-background-radius: 99; -fx-border-radius: 99; -fx-border-width: 2; -fx-border-color: "
					+ (c.equals(color[0]) ? "white" : "transparent") + ";");
		});
		for (var c : Ui.HABIT_COLORS) {
			var sw = new Button();
			sw.setMinSize(22, 22);
			sw.setMaxSize(22, 22);
			sw.setUserData(c);
			sw.setOnAction(e -> {
				color[0] = c;
				paint.run();
			});
			swatches.add(sw);
		}
		paint.run();
		colorRow.getChildren().addAll(swatches);

		var form = new VBox(12, Ui.row(icon, name), desc, dayRow, colorRow);
		form.setPadding(new Insets(10));
		form.setPrefWidth(520);
		dialog.getDialogPane().setContent(form);
		dialog.setOnShown(e -> name.requestFocus());
		dialog.setResultConverter(bt -> {
			if (bt != ButtonType.OK || name.getText().isBlank()) {
				return null;
			}
			var days = dayButtons.stream().filter(ToggleButton::isSelected).map(b -> (String) b.getUserData()).toList();
			return body("name", name.getText().trim(), "icon", icon.getText().isBlank() ? null : icon.getText().trim(),
					"description", desc.getText().isBlank() ? null : desc.getText().trim(), "color", color[0], "days", days,
					"archived", habit != null && habit.archived());
		});
		dialog.showAndWait().ifPresent(req -> Ui.run(
				() -> {
					if (habit == null) {
						api.createHabit(req);
					}
					else {
						api.updateHabit(habit.id(), req);
					}
				}, this::refresh));
	}
}
