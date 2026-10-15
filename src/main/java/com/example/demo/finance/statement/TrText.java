package com.example.demo.finance.statement;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

/** Ekstre metinleri için Türkçe yardımcılar: tutar, tarih ve karşılaştırma için sadeleştirme. */
final class TrText {

	static final Locale TR = Locale.forLanguageTag("tr-TR");
	static final String AMOUNT = "\\d{1,3}(?:\\.\\d{3})*,\\d{2}";

	private static final Map<String, Integer> MONTHS = Map.ofEntries(
			Map.entry("OCAK", 1), Map.entry("SUBAT", 2), Map.entry("MART", 3), Map.entry("NISAN", 4),
			Map.entry("MAYIS", 5), Map.entry("HAZIRAN", 6), Map.entry("TEMMUZ", 7), Map.entry("AGUSTOS", 8),
			Map.entry("EYLUL", 9), Map.entry("EKIM", 10), Map.entry("KASIM", 11), Map.entry("ARALIK", 12));

	private TrText() {
	}

	/** "3.752,22" → 3752.22; "+149.420,19" → 149420.19 */
	static BigDecimal amount(String s) {
		return new BigDecimal(s.replace("+", "").replace(".", "").replace(",", ".").trim());
	}

	/** "14 Eylül 2026" → 2026-09-14 */
	static LocalDate longDate(String day, String month, String year) {
		var m = MONTHS.get(fold(month));
		if (m == null) {
			throw new IllegalArgumentException("Bilinmeyen ay: " + month);
		}
		return LocalDate.of(Integer.parseInt(year), m, Integer.parseInt(day));
	}

	/**
	 * Karşılaştırma için sadeleştirir: büyük harf, Türkçe harfler ASCII'ye ("İSTANBUL", "ISTANBUL" → "ISTANBUL"),
	 * boşluklar teke.
	 */
	static String fold(String s) {
		var t = s.toUpperCase(TR).replace('İ', 'I').replace('Ğ', 'G').replace('Ü', 'U')
				.replace('Ş', 'S').replace('Ö', 'O').replace('Ç', 'C');
		t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return t.replaceAll("\\s+", " ").trim();
	}
}
