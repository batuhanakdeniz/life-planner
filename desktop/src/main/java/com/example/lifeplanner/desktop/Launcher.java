package com.example.lifeplanner.desktop;

import java.util.Locale;

import javafx.application.Application;

/**
 * Giriş noktası. JavaFX'i modül yolu olmadan (düz classpath ile) çalıştırabilmek için
 * {@link Application}'dan türemeyen ayrı bir main sınıfı kullanılır.
 */
public final class Launcher {

	private Launcher() {
	}

	public static void main(String[] args) {
		// Tarih seçicileri gg.aa.yyyy göstersin. Yalnızca FORMAT kategorisi değişir; toUpperCase() gibi
		// metin işlemleri Türkçe i/İ kuralından etkilenmesin diye genel yerel ayara dokunulmaz.
		Locale.setDefault(Locale.Category.FORMAT, Locale.forLanguageTag("tr-TR"));
		Application.launch(LifePlannerApp.class, args);
	}
}
