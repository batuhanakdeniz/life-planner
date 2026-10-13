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

	/**
	 * Menü çubuğu ikonu: logonun tek renk (template) sürümü, koyu menü çubuğunda da görünsün diye beyaza çevrilir.
	 * Kaynak bulunamazsa eski basit halka çizilir.
	 */
	private static BufferedImage icon() {
		try (var in = TrayManager.class.getResourceAsStream("menubar-iconTemplate@2x.png")) {
			if (in != null) {
				var src = javax.imageio.ImageIO.read(in);
				var out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
				for (int y = 0; y < src.getHeight(); y++) {
					for (int x = 0; x < src.getWidth(); x++) {
						int alpha = (src.getRGB(x, y) >>> 24) & 0xFF;
						out.setRGB(x, y, (alpha << 24) | 0xFFFFFF);
					}
				}
				return out;
			}
		}
		catch (java.io.IOException e) {
			// yedek ikona düş
		}
		int s = 44;
		var img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		var g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(java.awt.Color.WHITE);
		g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.drawOval(4, 4, s - 8, s - 8);
		g.dispose();
		return img;
	}
}
