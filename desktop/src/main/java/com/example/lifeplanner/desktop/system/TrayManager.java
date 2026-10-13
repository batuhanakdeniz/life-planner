package com.example.lifeplanner.desktop.system;

import java.awt.BasicStroke;
import java.awt.CheckboxMenuItem;
import java.awt.EventQueue;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;

import javafx.application.Platform;

/** macOS menü çubuğu (Windows'ta sistem tepsisi) ikonu. */
public class TrayManager {

	public record Actions(Runnable open, Runnable mini, Runnable toggleOnTop, Runnable reminderSettings, Runnable quit) {
	}

	private CheckboxMenuItem onTopItem;

	public static boolean supported() {
		try {
			return SystemTray.isSupported();
		}
		catch (Throwable e) {
			return false;
		}
	}

	public void install(Actions actions, boolean pinned) {
		EventQueue.invokeLater(() -> {
			try {
				var menu = new PopupMenu();
				menu.add(item("Hayat Planlayıcı'yı aç", actions.open()));
				menu.add(item("Mini mod", actions.mini()));
				onTopItem = new CheckboxMenuItem("Her zaman üstte", pinned);
				onTopItem.addItemListener(e -> Platform.runLater(actions.toggleOnTop()));
				menu.add(onTopItem);
				menu.add(item("Hatırlatıcı ayarları…", actions.reminderSettings()));
				menu.addSeparator();
				menu.add(item("Çıkış", actions.quit()));

				var icon = new TrayIcon(icon(), "Hayat Planlayıcı", menu);
				icon.setImageAutoSize(true);
				icon.addActionListener(e -> Platform.runLater(actions.open()));
				SystemTray.getSystemTray().add(icon);
				Notifier.setTrayIcon(icon);
			}
			catch (Exception e) {
				System.err.println("Menü çubuğu ikonu eklenemedi: " + e.getMessage());
			}
		});
	}

	public void setPinned(boolean pinned) {
		EventQueue.invokeLater(() -> {
			if (onTopItem != null) {
				onTopItem.setState(pinned);
			}
		});
	}

	private static MenuItem item(String label, Runnable action) {
		var item = new MenuItem(label);
		item.addActionListener(e -> Platform.runLater(action));
		return item;
	}

	/** Basit ikon: mor halka içinde onay işareti. */
	private static BufferedImage icon() {
		int s = 44;
		var img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		var g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new java.awt.Color(0xA7, 0x8B, 0xFA));
		g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.drawOval(4, 4, s - 8, s - 8);
		g.drawPolyline(new int[] { 13, 20, 32 }, new int[] { 23, 30, 15 }, 3);
		g.dispose();
		return img;
	}
}
