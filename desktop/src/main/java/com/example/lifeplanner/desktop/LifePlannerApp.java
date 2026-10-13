package com.example.lifeplanner.desktop;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import com.example.lifeplanner.desktop.api.ApiClient;
import com.example.lifeplanner.desktop.system.ReminderService;
import com.example.lifeplanner.desktop.system.Settings;
import com.example.lifeplanner.desktop.system.TrayManager;
import com.example.lifeplanner.desktop.ui.MainView;
import com.example.lifeplanner.desktop.ui.MiniView;
import com.example.lifeplanner.desktop.ui.Ui;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

public class LifePlannerApp extends Application {

	private static final double FULL_W = 1180, FULL_H = 820, MINI_W = 360, MINI_H = 560;

	private final ApiClient api = new ApiClient(Settings.apiUrl());
	private final TrayManager tray = new TrayManager();
	private final ReminderService reminders = new ReminderService(api);
	private Stage stage;
	private Scene scene;
	private MainView main;
	private MiniView mini;
	private boolean miniMode;

	@Override
	public void start(Stage stage) {
		this.stage = stage;
		main = new MainView(api, new MainView.Actions(this::toggleOnTop, this::enterMini, this::openReminderSettings));
		mini = new MiniView(api, this::toggleOnTop, this::enterFull);

		scene = new Scene(main, FULL_W, FULL_H);
		scene.getStylesheets().add(getClass().getResource("app.css").toExternalForm());
		stage.setTitle("Hayat Planlayıcı");
		stage.setScene(scene);
		stage.setMinWidth(MINI_W);
		applyOnTop(Settings.alwaysOnTop());

		// Pencereye dönünce verileri tazele (ör. web'de yapılan değişiklikler)
		stage.focusedProperty().addListener((o, was, focused) -> {
			if (focused) {
				refreshCurrent();
			}
		});

		if (TrayManager.supported()) {
			// Pencere kapatılınca uygulama menü çubuğunda çalışmaya devam eder
			Platform.setImplicitExit(false);
			stage.setOnCloseRequest(e -> {
				e.consume();
				stage.hide();
			});
			tray.install(new TrayManager.Actions(this::showWindow, () -> { enterMini(); showWindow(); },
					this::toggleOnTop, this::openReminderSettings, this::quit), Settings.alwaysOnTop());
		}
		reminders.start();
		setDockIcon();
		stage.show();

		var snapshotDir = System.getProperty("lifeplanner.snapshot");
		if (snapshotDir != null) {
			takeSnapshots(new File(snapshotDir));
		}
	}

	/**
	 * macOS Dock ikonu. Uygulama bir .app paketi olarak değil düz Java süreci olarak çalıştığında
	 * varsayılan Java ikonu görünür; Taskbar API ile logo atanır. Desteklenmeyen ortamlarda sessizce geçilir.
	 */
	private void setDockIcon() {
		try {
			if (!java.awt.Taskbar.isTaskbarSupported()) {
				return;
			}
			var taskbar = java.awt.Taskbar.getTaskbar();
			if (!taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {
				return;
			}
			try (var in = getClass().getResourceAsStream("app-icon.png")) {
				if (in != null) {
					taskbar.setIconImage(javax.imageio.ImageIO.read(in));
				}
			}
		}
		catch (Exception e) {
			System.err.println("Dock ikonu ayarlanamadı: " + e.getMessage());
		}
	}

	private void refreshCurrent() {
		if (miniMode) {
			mini.refresh();
		}
		else {
			main.refresh();
		}
	}

	private void showWindow() {
		stage.show();
		stage.toFront();
		stage.requestFocus();
	}

	private void enterMini() {
		miniMode = true;
		scene.setRoot(mini);
		stage.setWidth(MINI_W);
		stage.setHeight(MINI_H);
		mini.refresh();
	}

	private void enterFull() {
		miniMode = false;
		scene.setRoot(main);
		stage.setWidth(FULL_W);
		stage.setHeight(FULL_H);
		main.refresh();
	}

	private void toggleOnTop() {
		boolean pinned = !Settings.alwaysOnTop();
		Settings.setAlwaysOnTop(pinned);
		applyOnTop(pinned);
	}

	private void applyOnTop(boolean pinned) {
		stage.setAlwaysOnTop(pinned);
		main.setPinned(pinned);
		mini.setPinned(pinned);
		tray.setPinned(pinned);
	}

	private void openReminderSettings() {
		showWindow();
		var dialog = new Dialog<ButtonType>();
		dialog.setTitle("Hatırlatıcılar");
		dialog.setHeaderText("Belirlediğin saatlerde günün kalan işleri bildirim olarak gösterilir.");
		dialog.getDialogPane().getStylesheets().addAll(scene.getStylesheets());
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		var enabled = new CheckBox("Hatırlatıcılar açık");
		enabled.setSelected(Settings.remindersEnabled());
		var times = new TextField(Settings.reminderTimesText());
		times.setPromptText("09:00, 21:00");
		var hint = Ui.label("Saatleri virgülle ayır (ör. 09:00, 13:30, 21:00).", "muted");
		var test = Ui.button("Test bildirimi gönder", () -> new Thread(reminders::sendNow).start());

		var form = new VBox(12, enabled, Ui.label("Saatler", "h2"), times, hint, test);
		form.setPadding(new Insets(10));
		form.setPrefWidth(420);
		dialog.getDialogPane().setContent(form);

		dialog.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
			var parsed = Settings.parseTimes(times.getText());
			Settings.setRemindersEnabled(enabled.isSelected());
			Settings.setReminderTimesText(String.join(", ", parsed.stream().map(Object::toString).toList()));
		});
	}

	private void quit() {
		Platform.exit();
		System.exit(0);
	}

	/** Geliştirme yardımcısı: tüm ekranların görüntüsünü PNG olarak kaydedip çıkar. */
	private void takeSnapshots(File dir) {
		dir.mkdirs();
		List<Runnable> steps = new ArrayList<>();
		String[] names = { "today", "tasks", "habits", "goals", "journal", "finance", "bulletin" };
		for (int i = 0; i < main.pageCount(); i++) {
			int index = i;
			steps.add(() -> main.show(index));
			steps.add(() -> save(dir, names[index]));
		}
		steps.add(() -> main.showFinanceView(1));
		steps.add(() -> save(dir, "finance-trades"));
		steps.add(() -> main.showFinanceView(2));
		steps.add(() -> save(dir, "finance-payments"));
		steps.add(() -> main.showFinanceView(0));
		steps.add(this::enterMini);
		steps.add(() -> save(dir, "mini"));
		steps.add(this::quit);
		runSteps(steps, 0);
	}

	private void runSteps(List<Runnable> steps, int i) {
		if (i >= steps.size()) {
			return;
		}
		var pause = new PauseTransition(Duration.millis(1500));
		pause.setOnFinished(e -> {
			steps.get(i).run();
			runSteps(steps, i + 1);
		});
		pause.play();
	}

	private void save(File dir, String name) {
		try {
			var image = scene.snapshot(null);
			ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", new File(dir, name + ".png"));
		}
		catch (Exception e) {
			System.err.println("Ekran görüntüsü alınamadı: " + e.getMessage());
		}
	}
}
