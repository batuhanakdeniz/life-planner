package com.example.lifeplanner.desktop.ui;

import java.util.List;

import com.example.lifeplanner.desktop.api.ApiClient;

import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/** Tam uygulama görünümü: sol menü + seçili sayfa. */
public class MainView extends BorderPane {

	public record Actions(Runnable toggleOnTop, Runnable miniMode, Runnable reminderSettings) {
	}

	private final List<Page> pages;
	private final List<ToggleButton> navButtons = new java.util.ArrayList<>();
	private final ScrollPane content = new ScrollPane();
	private final ToggleButton pin = new ToggleButton("📌 Her zaman üstte");
	private int current;

	public MainView(ApiClient api, Actions actions) {
		pages = List.of(new TodayPage(api), new TasksPage(api), new HabitsPage(api), new GoalsPage(api), new JournalPage(api), new FinancePage(api), new BulletinPage(api));
		String[] labels = { "☀️  Bugün", "✓  Görevler", "🔁  Alışkanlıklar", "🎯  Hedefler", "📓  Günlük", "💰  Finans", "📰  Bülten" };

		var group = new ToggleGroup();
		var nav = new VBox(2);
		for (int i = 0; i < labels.length; i++) {
			int index = i;
			var b = new ToggleButton(labels[i]);
			b.getStyleClass().add("nav-button");
			b.setMaxWidth(Double.MAX_VALUE);
			b.setToggleGroup(group);
			b.setOnAction(e -> show(index));
			navButtons.add(b);
			nav.getChildren().add(b);
		}

		pin.getStyleClass().addAll("nav-button", "pin");
		pin.setMaxWidth(Double.MAX_VALUE);
		pin.setOnAction(e -> actions.toggleOnTop().run());
		var mini = Ui.button("⤡  Mini mod", actions.miniMode(), "nav-button");
		mini.setMaxWidth(Double.MAX_VALUE);
		var reminders = Ui.button("🔔  Hatırlatıcılar", actions.reminderSettings(), "nav-button");
		reminders.setMaxWidth(Double.MAX_VALUE);

		var sidebar = new VBox(4, Ui.label("🌱 Hayat Planlayıcı", "brand"), nav, Ui.spacer(), pin, mini, reminders);
		sidebar.getStyleClass().add("sidebar");

		content.setFitToWidth(true);
		content.getStyleClass().add("content-scroll");

		setLeft(sidebar);
		setCenter(content);
		show(0);
	}

	public void show(int index) {
		current = index;
		navButtons.get(index).setSelected(true);
		content.setContent(pages.get(index));
		content.setVvalue(0);
		pages.get(index).refresh();
	}

	/** Ekran görüntüsü modu için finans alt sekmesini açar. */
	public void showFinanceView(int index) {
		pages.stream().filter(FinancePage.class::isInstance).map(FinancePage.class::cast).findFirst()
				.ifPresent(f -> f.showView(index));
	}

	public int pageCount() {
		return pages.size();
	}

	public void refresh() {
		pages.get(current).refresh();
	}

	public void setPinned(boolean pinned) {
		pin.setSelected(pinned);
	}
}
