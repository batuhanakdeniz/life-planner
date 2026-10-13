package com.example.lifeplanner.desktop.system;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.prefs.Preferences;

/** Kalıcı kullanıcı ayarları (macOS'ta ~/Library/Preferences altında saklanır). */
public final class Settings {

	private static final Preferences PREFS = Preferences.userNodeForPackage(Settings.class);

	private Settings() {
	}

	public static String apiUrl() {
		return System.getProperty("lifeplanner.api",
				Objects.requireNonNullElse(System.getenv("LIFEPLANNER_API"), "http://localhost:8080/api"));
	}

	public static boolean alwaysOnTop() {
		return PREFS.getBoolean("alwaysOnTop", false);
	}

	public static void setAlwaysOnTop(boolean value) {
		PREFS.putBoolean("alwaysOnTop", value);
	}

	public static boolean remindersEnabled() {
		return PREFS.getBoolean("reminders.enabled", true);
	}

	public static void setRemindersEnabled(boolean value) {
		PREFS.putBoolean("reminders.enabled", value);
	}

	/** Hatırlatıcı saatleri, ör. "09:00, 21:00". */
	public static String reminderTimesText() {
		return PREFS.get("reminders.times", "09:00, 21:00");
	}

	public static void setReminderTimesText(String value) {
		PREFS.put("reminders.times", value);
	}

	public static List<LocalTime> reminderTimes() {
		return parseTimes(reminderTimesText());
	}

	/** Geçersiz saatleri atlar. */
	public static List<LocalTime> parseTimes(String text) {
		return Arrays.stream(text.split("[,;\\s]+"))
				.filter(s -> !s.isBlank())
				.map(s -> {
					try {
						return LocalTime.parse(s.length() == 4 ? "0" + s : s);
					}
					catch (DateTimeParseException e) {
						return null;
					}
				})
				.filter(Objects::nonNull)
				.sorted()
				.toList();
	}
}
