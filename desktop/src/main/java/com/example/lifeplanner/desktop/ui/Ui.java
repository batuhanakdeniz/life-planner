package com.example.lifeplanner.desktop.ui;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Arayüz yardımcıları: ortak bileşenler, tarih biçimleri, arka plan işleri. */
public final class Ui {

	public static final Locale TR = Locale.forLanguageTag("tr-TR");
	public static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("d MMMM EEEE", TR);
	public static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("d MMMM yyyy EEEE", TR);
	public static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMMM", TR);
	public static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("d MMM", TR);
	public static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", TR);
	public static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEE", TR);

	public static final String[] MOODS = { "😞", "😕", "😐", "🙂", "😄" };
	public static final Map<String, String> PRIORITY = ordered("HIGH", "Yüksek", "MEDIUM", "Orta", "LOW", "Düşük");
	public static final Map<String, String> TASK_STATUS = ordered("TODO", "Yapılacak", "IN_PROGRESS", "Yapılıyor", "DONE", "Bitti");
	public static final Map<String, String> GOAL_STATUS = ordered("ACTIVE", "Aktif", "COMPLETED", "Tamamlandı", "PAUSED", "Beklemede", "ABANDONED", "Bırakıldı");
	public static final Map<String, String> CATEGORY = ordered("HEALTH", "Sağlık", "CAREER", "Kariyer", "FINANCE", "Finans",
			"EDUCATION", "Eğitim", "PERSONAL", "Kişisel", "RELATIONSHIPS", "İlişkiler", "TRAVEL", "Seyahat", "OTHER", "Diğer");
	public static final Map<String, String> CATEGORY_ICON = ordered("HEALTH", "💪", "CAREER", "💼", "FINANCE", "💰",
			"EDUCATION", "🎓", "PERSONAL", "🌱", "RELATIONSHIPS", "❤️", "TRAVEL", "✈️", "OTHER", "⭐");
	public static final String[] HABIT_COLORS = { "#a78bfa", "#22d3ee", "#4ade80", "#fbbf24", "#f472b6", "#fb923c", "#60a5fa", "#f87171" };

	private static final ExecutorService IO = Executors.newFixedThreadPool(4, r -> {
		var t = new Thread(r, "api-io");
		t.setDaemon(true);
		return t;
	});

	private Ui() {
	}

	private static Map<String, String> ordered(String... kv) {
		var map = new LinkedHashMap<String, String>();
		for (int i = 0; i < kv.length; i += 2) {
			map.put(kv[i], kv[i + 1]);
		}
		return map;
	}

	/** İşi arka planda çalıştırır, sonucu arayüz iş parçacığında teslim eder; hata olursa uyarı gösterir. */
	public static <T> void async(Callable<T> work, Consumer<T> onSuccess) {
		CompletableFuture.supplyAsync(() -> {
			try {
				return work.call();
			}
			catch (Exception e) {
				throw new CompletionException(e);
			}
		}, IO).whenComplete((result, ex) -> Platform.runLater(() -> {
			if (ex != null) {
				error(ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex);
			}
			else {
				onSuccess.accept(result);
			}
		}));
	}

	/** Sonuç döndürmeyen API çağrısı, ardından {@code then} çalışır (genelde yenileme). */
	public static void run(ThrowingRunnable work, Runnable then) {
		async(() -> {
			work.run();
			return Boolean.TRUE;
		}, ok -> then.run());
	}

	@FunctionalInterface
	public interface ThrowingRunnable {
		void run() throws Exception;
	}

	private static boolean errorShowing;

	public static void error(Throwable ex) {
		if (errorShowing) {
			return; // Aynı anda birden fazla hata penceresi açılmasın
		}
		errorShowing = true;
		var alert = new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK);
		alert.setHeaderText("Bir sorun oluştu");
		alert.showAndWait();
		errorShowing = false;
	}

	public static boolean confirm(String message) {
		var alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
		alert.setHeaderText(null);
		return alert.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
	}

	/** Bağlantıyı varsayılan tarayıcıda açar. */
	public static void openUrl(String url) {
		java.awt.EventQueue.invokeLater(() -> {
			try {
				java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
			}
			catch (Exception e) {
				Platform.runLater(() -> error(new IllegalStateException("Bağlantı açılamadı: " + url)));
			}
		});
	}

	public static Label label(String text, String... styleClasses) {
		var l = new Label(text);
		l.getStyleClass().addAll(styleClasses);
		return l;
	}

	public static Label tag(String text, String variant) {
		var l = label(text, "tag");
		if (variant != null) {
			l.getStyleClass().add(variant);
		}
		return l;
	}

	public static Button button(String text, Runnable action, String... styleClasses) {
		var b = new Button(text);
		b.getStyleClass().addAll(styleClasses);
		b.setOnAction(e -> action.run());
		return b;
	}

	public static VBox card(Node... children) {
		var box = new VBox(10, children);
		box.getStyleClass().add("card");
		return box;
	}

	public static Region spacer() {
		var r = new Region();
		HBox.setHgrow(r, Priority.ALWAYS);
		VBox.setVgrow(r, Priority.ALWAYS);
		return r;
	}

	public static HBox row(Node... children) {
		var row = new HBox(8, children);
		row.setAlignment(Pos.CENTER_LEFT);
		return row;
	}

	public static ProgressBar progress(int percent) {
		var bar = new ProgressBar(percent / 100.0);
		bar.setMaxWidth(Double.MAX_VALUE);
		return bar;
	}

	public static Label empty(String text) {
		var l = label(text, "empty");
		l.setMaxWidth(Double.MAX_VALUE);
		return l;
	}

	/** "salı" → "Salı" (Türkçe kurallarıyla). */
	public static String cap(String s) {
		return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(TR) + s.substring(1);
	}

	public static LocalDate startOfWeek(LocalDate d) {
		return d.with(DayOfWeek.MONDAY);
	}

	public static String dayLabel(DayOfWeek d) {
		return switch (d) {
			case MONDAY -> "Pzt";
			case TUESDAY -> "Sal";
			case WEDNESDAY -> "Çar";
			case THURSDAY -> "Per";
			case FRIDAY -> "Cum";
			case SATURDAY -> "Cmt";
			case SUNDAY -> "Paz";
		};
	}
}
