package com.example.demo.finance.market;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;

import com.example.demo.finance.Currency;

import tools.jackson.databind.json.JsonMapper;

/** Dış kaynak yanıtlarını ayrıştırır. Ağdan bağımsızdır; birim testleri örnek yanıtlarla çalışır. */
public final class MarketParsers {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private MarketParsers() {
	}

	/** Yahoo'dan dönen anlık fiyat. */
	public record Quote(BigDecimal price, String currency) {
	}

	/**
	 * Truncgil today.json → anahtar başına <b>alış</b> fiyatı.
	 * Örn. "gram-altin": {"Alış": "6.591,46", ...}, "ons": {"Alış": "$4.153,12"}
	 */
	public static Map<String, BigDecimal> truncgil(String json) {
		var root = JSON.readTree(json);
		var result = new LinkedHashMap<String, BigDecimal>();
		for (var entry : root.properties()) {
			var buy = entry.getValue().path("Alış");
			if (buy.isString()) {
				var value = turkishNumber(buy.asString());
				if (value != null) {
					result.put(entry.getKey(), value);
				}
			}
		}
		return result;
	}

	/** "6.591,46" → 6591.46, "$4.153,12" → 4153.12. Çözülemezse null. */
	static BigDecimal turkishNumber(String s) {
		var t = s.replaceAll("[^0-9.,-]", "").replace(".", "").replace(",", ".");
		if (t.isEmpty()) {
			return null;
		}
		try {
			return new BigDecimal(t);
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	/** TCMB kur XML'i → döviz başına döviz alış kuru (ForexBuying). */
	public static Map<Currency, BigDecimal> tcmb(String xml) {
		try {
			var factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			var doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
			var result = new EnumMap<Currency, BigDecimal>(Currency.class);
			var nodes = doc.getElementsByTagName("Currency");
			for (int i = 0; i < nodes.getLength(); i++) {
				var el = (Element) nodes.item(i);
				Currency currency;
				try {
					currency = Currency.valueOf(el.getAttribute("Kod"));
				}
				catch (IllegalArgumentException e) {
					continue; // Takip etmediğimiz döviz
				}
				var buying = el.getElementsByTagName("ForexBuying");
				var unit = el.getElementsByTagName("Unit");
				if (buying.getLength() == 0 || buying.item(0).getTextContent().isBlank()) {
					continue;
				}
				var rate = new BigDecimal(buying.item(0).getTextContent().trim());
				if (unit.getLength() > 0) {
					var u = new BigDecimal(unit.item(0).getTextContent().trim());
					if (u.compareTo(BigDecimal.ONE) != 0) {
						rate = rate.divide(u, java.math.MathContext.DECIMAL64);
					}
				}
				result.put(currency, rate);
			}
			return result;
		}
		catch (Exception e) {
			throw new IllegalStateException("TCMB yanıtı çözülemedi: " + e.getMessage(), e);
		}
	}

	/** Yahoo chart yanıtı → son fiyat ve para birimi. */
	public static Quote yahoo(String json) {
		var root = JSON.readTree(json);
		var error = root.path("chart").path("error");
		if (!error.isMissingNode() && !error.isNull()) {
			throw new IllegalStateException("Yahoo: " + error.path("description").asString("bilinmeyen hata"));
		}
		var meta = root.path("chart").path("result").path(0).path("meta");
		if (!meta.path("regularMarketPrice").isNumber()) {
			throw new IllegalStateException("Yahoo yanıtında fiyat yok");
		}
		return new Quote(meta.path("regularMarketPrice").decimalValue(), meta.path("currency").asString(null));
	}
}
