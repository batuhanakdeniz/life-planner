package com.example.lifeplanner.desktop.system;

import java.awt.TrayIcon;

/** İşletim sistemi bildirimi gösterir. macOS'ta osascript, diğerlerinde tepsi balonu kullanılır. */
public final class Notifier {

	private static final boolean MAC = System.getProperty("os.name", "").toLowerCase().contains("mac");
	private static volatile TrayIcon trayIcon;

	private Notifier() {
	}

	static void setTrayIcon(TrayIcon icon) {
		trayIcon = icon;
	}

	public static void notify(String title, String message) {
		if (MAC) {
			try {
				var script = "display notification \"" + escape(message) + "\" with title \"" + escape(title) + "\" sound name \"Glass\"";
				new ProcessBuilder("osascript", "-e", script).inheritIO().start();
				return;
			}
			catch (Exception e) {
				// tepsi balonuna düş
			}
		}
		var icon = trayIcon;
		if (icon != null) {
			java.awt.EventQueue.invokeLater(() -> icon.displayMessage(title, message, TrayIcon.MessageType.INFO));
		}
	}

	private static String escape(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
