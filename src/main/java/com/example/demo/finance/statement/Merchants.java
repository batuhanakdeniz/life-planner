package com.example.demo.finance.statement;

import java.util.Arrays;
import java.util.regex.Pattern;

/** Ekstre açıklamasından okunabilir iş yeri adı ve kural deseni önerisi çıkarır. */
final class Merchants {

	/** Sonda (bazen iş yeri adına yapışık) görülen şehirler. */
	private static final Pattern CITY = Pattern.compile(
			"(?:İ?STANBUL|ISTANBUL|İSTANBUL|ANKARA|İZMİR|IZMIR|KOCAELİ|KOCAELI|BURSA|ANTALYA|LONDON|CORK|LOS GATOS|"
					+ "SAN FRANCISCO\\p{Lu}{0,4}|DUBLIN|AMSTERDAM|LUXEMBOURG|PARIS|BERLIN)$");
	/**
	 * Sondaki ülke kodu ya da sayı ("TR", "IRIR", "GBGB", "79"). Ekstrede ayrı bir sütun olduğu için önünde en az
	 * iki boşluk vardır; tek boşlukla ayrılmış "GIDA", "EVI", "TİC" gibi kelimeler iş yeri adının parçasıdır.
	 */
	private static final Pattern COUNTRY = Pattern.compile("\\s{2,}(?:\\p{Lu}{2,4}|\\d{1,3})$");
	/** "SPOTIFY.COM", "MSBILL.INFO" gibi sondaki alan adı. */
	private static final Pattern DOMAIN_TAIL = Pattern.compile("\\s{2,}[\\w.-]+\\.(?:COM|INFO|NET|ORG|IO)$", Pattern.CASE_INSENSITIVE);

	private Merchants() {
	}

	/** "MONEYPAY    *DIJITAL PLATISTANBUL     TR" → "MONEYPAY *DIJITAL PLAT" */
	static String clean(String description) {
		var s = COUNTRY.matcher(description.strip()).replaceFirst("").strip();
		for (int i = 0; i < 2; i++) {
			s = DOMAIN_TAIL.matcher(s).replaceFirst("").strip();
			s = CITY.matcher(s).replaceFirst("").strip();
		}
		s = s.replaceAll("\\s+", " ").strip();
		return s.isEmpty() ? description.strip() : s;
	}

	/** Kural için önerilen desen: sadeleştirilmiş adın ilk iki kelimesi ("UBER EATS", "BOGAZICI BALIK"). */
	static String suggestPattern(String description) {
		var words = TrText.fold(clean(description)).split(" ");
		return String.join(" ", Arrays.copyOf(words, Math.min(words.length, 2)));
	}
}
