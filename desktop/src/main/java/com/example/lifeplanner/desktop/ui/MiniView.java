package com.example.lifeplanner.desktop.ui;

import static com.example.lifeplanner.desktop.api.ApiClient.body;

import java.time.LocalDate;

import com.example.lifeplanner.desktop.api.ApiClient;
import com.example.lifeplanner.desktop.api.Models.Dashboard;
import com.example.lifeplanner.desktop.api.Models.Habit;

import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Küçük "Bugün" penceresi: bugünün görevleri, alışkanlıkları ve hızlı ekleme. */
public class MiniView extends VBox {

	private final ApiClient api;
	private final VBox list = new VBox(4);
	private final ToggleButton pin = new ToggleButton("📌");
	private final javafx.scene.control.Label header = Ui.label("", "mini-header");
	private final javafx.scene.control.Label summary = Ui.label("", "muted");

	public MiniView(ApiClient api, Runnable toggleOnTop, Runnable expand) {
		this.api = api;
		getStyleClass().add("mini");

		pin.getStyleClass().add("pin");
		pin.setTooltip(new javafx.scene.control.Tooltip("Her zaman üstte"));
		pin.setOnAction(e -> toggleOnTop.run());
		var expandBtn = Ui.button("⤢", expand);
		expandBtn.setTooltip(new javafx.scene.control.Tooltip("Tam uygulamayı aç"));

		var scroll = new ScrollPane(list);
		scroll.setFitToWidth(true);
		VBox.setVgrow(scroll, Priority.ALWAYS);

		var quick = TaskWidgets.quickAdd("+ Hızlı görev… (Enter)",
				t -> Ui.run(() -> api.createTask(body("title", t, "dueDate", LocalDate.now())), this::refresh));
		quick.getStyleClass().remove("quick-add");

		getChildren().addAll(Ui.row(header, Ui.spacer(), pin, expandBtn), summary, scroll, quick);
	}

	public void refresh() {
		Ui.async(() -> api.dashboard(LocalDate.now()), this::render);
	}

	public void setPinned(boolean pinned) {
		pin.setSelected(pinned);
	}

	private void render(Dashboard d) {
		header.setText("🌱 Bugün · " + d.date().format(Ui.SHORT));
		var s = d.summary();
		summary.setText("Görev " + s.tasksDone() + "/" + s.tasksTotal() + "   ·   Alışkanlık " + s.habitsDone() + "/" + s.habitsScheduled());

		list.getChildren().clear();
		if (d.tasks().isEmpty()) {
			list.getChildren().add(Ui.empty("Bugün için görev yok 🎉"));
		}
		d.tasks().forEach(t -> list.getChildren().add(TaskWidgets.taskRow(api, t, this::refresh, false, false)));

		if (d.duePayments() != null && !d.duePayments().isEmpty()) {
			var title = Ui.label("Yaklaşan ödemeler", "muted");
			title.setStyle("-fx-padding: 10 0 0 0;");
			list.getChildren().add(title);
			d.duePayments().forEach(p -> list.getChildren().add(FinancePage.paymentRow(api, p, this::refresh, true, null)));
		}

		var habits = d.habits().stream().filter(Habit::scheduled).toList();
		if (!habits.isEmpty()) {
			var title = Ui.label("Alışkanlıklar", "muted");
			title.setStyle("-fx-padding: 10 0 0 0;");
			list.getChildren().add(title);
			habits.forEach(h -> list.getChildren().add(TodayPage.habitRowBase(api, h, LocalDate.now(), this::refresh)));
		}
	}
}
