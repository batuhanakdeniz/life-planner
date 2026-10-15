package com.example.demo.bulletin;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.example.demo.common.vault.MarkdownTables;
import com.example.demo.common.vault.VaultTableSync;

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
			sb.append("| ").append(MarkdownTables.escape(r.name())).append(" | ").append(r.handle()).append(" | ")
					.append(r.active() ? "evet" : "hayır").append(" | ")
					.append(String.format("%02d:%02d", r.cutoff().getHour(), r.cutoff().getMinute())).append(" | ")
					.append(r.lookbackHours()).append(" |\n");
		}
		return sb.toString();
	}

	static Parsed parse(String markdown) {
		var rows = new ArrayList<Row>();
		var warnings = new ArrayList<String>();
		for (var cells : MarkdownTables.rows(markdown, "Kanal")) {
			if (cells.size() < 2 || cells.get(1).isBlank()) {
				continue;
			}
			try {
				var name = cells.get(0).isBlank() ? cells.get(1) : cells.get(0);
				var handle = BulletinService.normalizeHandle(cells.get(1));
				boolean active = cells.size() < 3 || MarkdownTables.parseBool(cells.get(2));
				var cutoff = cells.size() < 4 || cells.get(3).isBlank() ? LocalTime.NOON : parseTime(cells.get(3));
				int lookback = cells.size() < 5 || cells.get(4).isBlank() ? 24 : Integer.parseInt(cells.get(4).trim());
				if (lookback < 1 || lookback > 168) {
					throw new IllegalArgumentException("Geriye (saat) 1–168 arası olmalı");
				}
				rows.add(new Row(name, handle, active, cutoff, lookback));
			}
			catch (RuntimeException e) {
				warnings.add("Okunamayan satır (| " + String.join(" | ", cells) + " |): " + e.getMessage());
			}
		}
		return new Parsed(rows, warnings);
	}

	/** Satır kümesinin sıradan bağımsız özeti; hangi tarafın değiştiğini anlamak için. */
	static String hash(List<Row> rows) {
		return VaultTableSync.hashOf(rows.stream().map(ChannelTable::canonical).toList());
	}

	static String canonical(Row r) {
		return r.key() + '|' + r.name() + '|' + r.active() + '|' + r.cutoff() + '|' + r.lookbackHours();
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
}
