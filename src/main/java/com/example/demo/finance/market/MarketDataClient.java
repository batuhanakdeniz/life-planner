package com.example.demo.finance.market;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.example.demo.finance.Currency;
import com.example.demo.finance.market.MarketParsers.Quote;

/** Dış fiyat kaynaklarına HTTP erişimi. */
@Component
public class MarketDataClient {

	private static final String UA = "Mozilla/5.0 (LifePlanner kisisel kullanim)";
	private static final DateTimeFormatter TCMB_DAY = DateTimeFormatter.ofPattern("ddMMyyyy");
	private static final DateTimeFormatter TCMB_MONTH = DateTimeFormatter.ofPattern("yyyyMM");

	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	/** Geçmiş kurlar değişmez; aynı günü tekrar indirmemek için önbellek. */
	private final Map<LocalDate, Map<Currency, BigDecimal>> tcmbHistory = new ConcurrentHashMap<>();

	public Map<String, BigDecimal> truncgil() {
		return MarketParsers.truncgil(get("https://finans.truncgil.com/today.json"));
	}

	public Map<Currency, BigDecimal> tcmbToday() {
		return MarketParsers.tcmb(get("https://www.tcmb.gov.tr/kurlar/today.xml"));
	}

	public Quote yahoo(String symbol) {
		var s = URLEncoder.encode(symbol, StandardCharsets.UTF_8);
		return MarketParsers.yahoo(get("https://query1.finance.yahoo.com/v8/finance/chart/" + s + "?range=1d&interval=1d"));
	}

	/** Tarih ve o tarihte geçerli olan TCMB kuru. */
	public record DatedRate(LocalDate date, BigDecimal rate) {
	}

	/**
	 * Verilen gün için TCMB döviz alış kuru. Hafta sonu/tatil ya da henüz yayınlanmamışsa
	 * (TCMB her iş günü ~15:30'da yayınlar) geriye doğru en yakın iş gününün kuru döner.
	 */
	public Optional<DatedRate> tcmbRate(Currency currency, LocalDate date) {
		for (int back = 0; back < 10; back++) {
			var day = date.minusDays(back);
			var rates = tcmbOn(day);
			if (rates.isPresent() && rates.get().containsKey(currency)) {
				return Optional.of(new DatedRate(day, rates.get().get(currency)));
			}
		}
		return Optional.empty();
	}

	private Optional<Map<Currency, BigDecimal>> tcmbOn(LocalDate day) {
		if (day.isAfter(LocalDate.now())) {
			return Optional.empty();
		}
		var cached = tcmbHistory.get(day);
		if (cached != null) {
			return Optional.of(cached);
		}
		var url = "https://www.tcmb.gov.tr/kurlar/" + day.format(TCMB_MONTH) + "/" + day.format(TCMB_DAY) + ".xml";
		var body = getOrNull(url);
		if (body == null) {
			return Optional.empty();
		}
		var rates = MarketParsers.tcmb(body);
		// Bugünün dosyası gün içinde değişmez ama geçmiş günler kesin olarak önbelleğe alınabilir
		if (day.isBefore(LocalDate.now())) {
			tcmbHistory.put(day, rates);
		}
		return Optional.of(rates);
	}

	private String get(String url) {
		var body = getOrNull(url);
		if (body == null) {
			throw new IllegalStateException("Veri bulunamadı: " + url);
		}
		return body;
	}

	/** 404'te null döner; diğer hatalarda istisna fırlatır. */
	private String getOrNull(String url) {
		try {
			var req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).header("User-Agent", UA).GET().build();
			var res = http.send(req, BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (res.statusCode() == 404) {
				return null;
			}
			if (res.statusCode() >= 400) {
				throw new IllegalStateException("HTTP " + res.statusCode() + " (" + URI.create(url).getHost() + ")");
			}
			return res.body();
		}
		catch (IOException e) {
			throw new IllegalStateException(URI.create(url).getHost() + " erişilemedi: " + e.getMessage(), e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("İstek iptal edildi", e);
		}
	}
}
