package com.example.demo.bulletin;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Kanallar.md içindeki markdown tablosunu okur ve yazar. */
final class ChannelTable {

	/** Tablodaki bir satır. */
	record Row(String name, String handle, boolean active, LocalTime cutoff, int lookbackHours) {

		String key() {
			return handle.toLowerCase(Locale.ROOT);
		}
	}

	/** Okuma sonucu: geçerli satırlar ve okunamayan satırlar için uyarılar. */
	record Parsed(List<Row> rows, List<String> warnings) {
	}

	private ChannelTable() {
	}

	static String render(List<Row> rows) {
		var sb = new StringBuilder();
		sb.append("# Bülten kanalları\n\n");
		sb.append("Bu tabloyu düzenleyebilirsin. Değişiklikler en geç 5 dakika içinde (ve her bülten hazırlanmadan önce) uygulamaya aktarılır.\n\n");
		sb.append("- **Kanal:** `@kanaladi` ya da kanal bağlantısı\n");
		sb.append("- **Aktif:** evet / hayır\n");
		sb.append("- **Kesim:** taramanın bittiği saat, Türkiye saati (ör. 12:00)\n");
		sb.append("- **Geriye (saat):** kesimden geriye kaç saat taranacağı (1–168)\n");
		sb.append("- Satırı silmek kanalı listeden çıkarır; geçmiş bültenler korunur.\n\n");
		sb.append("| Ad | Kanal | Aktif | Kesim | Geriye (saat) |\n");
		sb.append("|---|---|---|---|---|\n");
		for (var r : rows) {
			sb.append("| ").append(escape(r.name())).append(" | ").append(r.handle()).append(" | ")
					.append(r.active() ? "evet" : "hayır").append(" | ")
					.append(String.format("%02d:%02d", r.cutoff().getHour(), r.cutoff().getMinute())).append(" | ")
					.append(r.lookbackHours()).append(" |\n");
		}
		return sb.toString();
	}

	static Parsed parse(String markdown) {
		var rows = new ArrayList<Row>();
		var warnings = new ArrayList<String>();
		boolean inTable = false;
		for (var line : markdown.split("\\R")) {
			var t = line.trim();
			if (!t.startsWith("|")) {
				inTable = false;
				continue;
			}
			var cells = splitRow(t);
			if (!inTable) {
				// Başlık satırı: "Kanal" sütunu olan ilk tablo
				inTable = cells.stream().anyMatch(c -> c.equalsIgnoreCase("Kanal"));
				continue;
			}
			if (cells.stream().allMatch(c -> c.matches(":?-+:?"))) {
				continue; // ayraç satırı
			}
			if (cells.size() < 2 || cells.get(1).isBlank()) {
				continue;
			}
			try {
				var name = cells.get(0).isBlank() ? cells.get(1) : cells.get(0);
				var handle = BulletinService.normalizeHandle(cells.get(1));
				boolean active = cells.size() < 3 || parseBool(cells.get(2));
				var cutoff = cells.size() < 4 || cells.get(3).isBlank() ? LocalTime.NOON : parseTime(cells.get(3));
				int lookback = cells.size() < 5 || cells.get(4).isBlank() ? 24 : Integer.parseInt(cells.get(4).trim());
				if (lookback < 1 || lookback > 168) {
					throw new IllegalArgumentException("Geriye (saat) 1–168 arası olmalı");
				}
				rows.add(new Row(name, handle, active, cutoff, lookback));
			}
			catch (RuntimeException e) {
				warnings.add("Okunamayan satır (" + t + "): " + e.getMessage());
			}
		}
		return new Parsed(rows, warnings);
	}

	/** Satır kümesinin sıradan bağımsız özeti; hangi tarafın değiştiğini anlamak için. */
	static String hash(List<Row> rows) {
		var canonical = new StringBuilder();
		rows.stream().sorted(Comparator.comparing(Row::key)).forEach(r -> canonical.append(r.key()).append('|').append(r.name())
				.append('|').append(r.active()).append('|').append(r.cutoff()).append('|').append(r.lookbackHours()).append('\n'));
		try {
			var digest = java.security.MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static List<String> splitRow(String line) {
		var inner = line.substring(1, line.endsWith("|") ? line.length() - 1 : line.length());
		var cells = new ArrayList<String>();
		for (var c : inner.split("(?<!\\\\)\\|", -1)) {
			cells.add(c.trim().replace("\\|", "|"));
		}
		return cells;
	}

	private static boolean parseBool(String s) {
		return switch (s.trim().toLowerCase(Locale.forLanguageTag("tr"))) {
			case "evet", "e", "true", "1", "aktif", "açık", "x", "✓", "" -> true;
			case "hayır", "hayir", "h", "false", "0", "pasif", "kapalı" -> false;
			default -> throw new IllegalArgumentException("Aktif sütunu 'evet' ya da 'hayır' olmalı");
		};
	}

	private static LocalTime parseTime(String s) {
		try {
			var t = s.trim().replace('.', ':');
			return LocalTime.parse(t.length() == 4 ? "0" + t : t);
		}
		catch (DateTimeParseException e) {
			throw new IllegalArgumentException("Kesim saati SS:DD biçiminde olmalı (ör. 12:00)");
		}
	}

	private static String escape(String s) {
		return s.replace("|", "\\|");
	}
}
