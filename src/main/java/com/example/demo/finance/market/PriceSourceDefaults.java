package com.example.demo.finance.market;

import java.util.Locale;
import java.util.Map;

import com.example.demo.finance.AssetType;
import com.example.demo.finance.Currency;

/** Yeni varlık için fiyat kaynağı önerisi. */
public final class PriceSourceDefaults {

	/** Bizim sembolümüz → Truncgil anahtarı. */
	private static final Map<String, String> TRUNCGIL_KEYS = Map.of(
			"GRAM_ALTIN", "gram-altin",
			"CEYREK_ALTIN", "ceyrek-altin",
			"ATA_LIRA", "ata-altin",
			"YARIM_ALTIN", "yarim-altin",
			"TAM_ALTIN", "tam-altin",
			"CUMHURIYET_ALTINI", "cumhuriyet-altini",
			"GUMUS", "gumus",
			"ONS_ALTIN", "ons");

	public record Suggestion(PriceSource source, String sourceSymbol) {
	}

	private PriceSourceDefaults() {
	}

	public static Suggestion suggest(String symbol, AssetType type, Currency priceCurrency) {
		var s = symbol.toUpperCase(Locale.ROOT);
		return switch (type) {
			case CURRENCY -> (s.equals("USD") || s.equals("EUR")) && priceCurrency == Currency.TRY
					? new Suggestion(PriceSource.RATE, s) : new Suggestion(PriceSource.MANUAL, null);
			case GOLD -> TRUNCGIL_KEYS.containsKey(s)
					? new Suggestion(PriceSource.TRUNCGIL, TRUNCGIL_KEYS.get(s)) : new Suggestion(PriceSource.MANUAL, null);
			// BIST hisseleri Yahoo'da ".IS" sonekiyle, ABD hisseleri olduğu gibi
			case STOCK -> new Suggestion(PriceSource.YAHOO, priceCurrency == Currency.TRY ? s + ".IS" : s);
			case FUND -> new Suggestion(PriceSource.MANUAL, null);
		};
	}
}
