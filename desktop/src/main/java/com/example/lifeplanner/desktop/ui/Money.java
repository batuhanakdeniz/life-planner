package com.example.lifeplanner.desktop.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Map;
import java.util.regex.Pattern;

/** Para/sayı biçimlendirme ve Türkçe sayı girişi çözümleme (web'deki money.js ile aynı kurallar). */
public final class Money {

	public static final Map<String, String> ASSET_TYPE = Map.of("GOLD", "Altın", "CURRENCY", "Döviz", "STOCK", "Hisse", "FUND", "Fon");
	public static final Map<String, String> ASSET_ICON = Map.of("GOLD", "🥇", "CURRENCY", "💵", "STOCK", "📈", "FUND", "🧺");
	public static final Map<String, String> ASSET_COLOR = Map.of("GOLD", "#fbbf24", "CURRENCY", "#22d3ee", "STOCK", "#a78bfa", "FUND", "#4ade80");
	public static final Map<String, String> FREQUENCY = new java.util.LinkedHashMap<>();
	static {
		FREQUENCY.put("WEEKLY", "Haftalık");
		FREQUENCY.put("MONTHLY", "Aylık");
		FREQUENCY.put("YEARLY", "Yıllık");
	}

	private static final Pattern THOUSANDS = Pattern.compile("^\\d{1,3}(\\.\\d{3})+$");
	private static final Pattern PLAIN = Pattern.compile("^-?\\d+(\\.\\d+)?$");

	private Money() {
	}

	/** Web ile aynı sonucu vermesi için yarım değerler yukarı yuvarlanır (Java varsayılanı HALF_EVEN). */
	private static NumberFormat format() {
		var nf = NumberFormat.getNumberInstance(Ui.TR);
		nf.setRoundingMode(RoundingMode.HALF_UP);
		return nf;
	}

	public static String symbol(String currency) {
		return switch (currency == null ? "" : currency) {
			case "TRY" -> "₺";
			case "USD" -> "$";
			case "EUR" -> "€";
			default -> currency;
		};
	}

	public static String money(BigDecimal v, String currency, int digits) {
		if (v == null) {
			return "—";
		}
		var nf = format();
		nf.setMinimumFractionDigits(digits);
		nf.setMaximumFractionDigits(digits);
		var text = nf.format(v.abs());
		return (v.signum() < 0 ? "−" : "") + symbol(currency) + text;
	}

	public static String money(BigDecimal v, String currency) {
		return money(v, currency, 2);
	}

	public static String signed(BigDecimal v, String currency, int digits) {
		if (v == null) {
			return "—";
		}
		return (v.signum() > 0 ? "+" : "") + money(v, currency, digits);
	}

	public static String num(BigDecimal v) {
		if (v == null) {
			return "—";
		}
		var nf = format();
		nf.setMaximumFractionDigits(4);
		return nf.format(v);
	}

	public static String pct(BigDecimal v) {
		if (v == null) {
			return "";
		}
		var nf = format();
		nf.setMinimumFractionDigits(2);
		nf.setMaximumFractionDigits(2);
		return (v.signum() > 0 ? "+" : v.signum() < 0 ? "−" : "") + "%" + nf.format(v.abs());
	}

	/** Kâr/zarar rengi için CSS sınıfı. */
	public static String pnl(BigDecimal v) {
		return v == null || v.signum() == 0 ? "muted" : v.signum() > 0 ? "pos" : "neg";
	}

	/**
	 * Türkçe sayı girişini çözer: virgül ondalık, nokta binlik ayırıcıdır.
	 * @return boş girişte null; geçersizse IllegalArgumentException
	 */
	public static BigDecimal parse(String s) {
		if (s == null || s.isBlank()) {
			return null;
		}
		var t = s.trim().replaceAll("\\s", "");
		if (t.contains(",")) {
			if (t.indexOf(',') != t.lastIndexOf(',')) {
				throw new IllegalArgumentException("Geçersiz sayı: " + s);
			}
			t = t.replace(".", "").replace(",", ".");
		}
		else if (THOUSANDS.matcher(t).matches()) {
			t = t.replace(".", "");
		}
		if (!PLAIN.matcher(t).matches()) {
			throw new IllegalArgumentException("Geçersiz sayı: " + s);
		}
		return new BigDecimal(t);
	}

	/** Düzenleme alanına yazmak için: 45.2 → "45,2" */
	public static String input(BigDecimal v) {
		return v == null ? "" : v.stripTrailingZeros().toPlainString().replace('.', ',');
	}
}
