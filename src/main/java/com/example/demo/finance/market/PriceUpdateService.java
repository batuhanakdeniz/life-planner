package com.example.demo.finance.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.demo.finance.Currency;
import com.example.demo.finance.PortfolioService;

/**
 * Fiyatları dış kaynaklardan çeker. Ağ çağrıları transaction dışında yapılır;
 * veritabanı yazımları PortfolioService'in transactional metotlarıyla olur.
 * Bir kaynak hata verirse diğerleri etkilenmez ve son bilinen fiyat kalır.
 */
@Service
public class PriceUpdateService {

	private static final Logger log = LoggerFactory.getLogger(PriceUpdateService.class);
	/** Bir çalıştırmada en fazla kaç tahmini kur düzeltilsin (TCMB'yi yormamak için). */
	private static final int MAX_RATE_FIXES = 30;

	private final MarketDataClient client;
	private final PortfolioService portfolio;
	private final ReentrantLock running = new ReentrantLock();
	private final AtomicReference<RefreshStatus> last = new AtomicReference<>();

	public PriceUpdateService(MarketDataClient client, PortfolioService portfolio) {
		this.client = client;
		this.portfolio = portfolio;
	}

	/** @param sources kaynak adı → "ok" ya da hata mesajı */
	public record RefreshStatus(Instant finishedAt, int updated, int failed, int ratesFixed, Map<String, String> sources) {
	}

	public RefreshStatus lastStatus() {
		return last.get();
	}

	/** Tüm kaynakları günceller. Aynı anda ikinci bir çalıştırma beklemeden son durumu döner. */
	public RefreshStatus refreshAll() {
		if (!running.tryLock()) {
			return last.get();
		}
		try {
			var sources = new LinkedHashMap<String, String>();
			int[] counts = new int[2]; // güncellenen, hatalı
			refreshRates(sources);
			refreshTruncgil(sources, counts);
			refreshYahoo(sources, counts);
			int fixed = fixEstimatedRates(sources);
			var status = new RefreshStatus(Instant.now(), counts[0], counts[1], fixed, sources);
			last.set(status);
			log.info("Fiyat güncellemesi: {} güncellendi, {} hatalı, {} kur düzeltildi {}", counts[0], counts[1], fixed, sources);
			return status;
		}
		finally {
			running.unlock();
		}
	}

	private void refreshRates(Map<String, String> sources) {
		try {
			var rates = client.tcmbToday();
			for (var c : List.of(Currency.USD, Currency.EUR)) {
				if (rates.containsKey(c)) {
					portfolio.setRate(c, rates.get(c));
				}
			}
			sources.put("TCMB", "ok");
		}
		catch (RuntimeException e) {
			sources.put("TCMB", e.getMessage());
		}
	}

	private void refreshTruncgil(Map<String, String> sources, int[] counts) {
		var assets = portfolio.assetsWithSource(PriceSource.TRUNCGIL);
		if (assets.isEmpty()) {
			return;
		}
		Map<String, BigDecimal> prices;
		try {
			prices = client.truncgil();
			sources.put("Truncgil", "ok");
		}
		catch (RuntimeException e) {
			sources.put("Truncgil", e.getMessage());
			assets.forEach(a -> portfolio.markPriceError(a.getId(), "Truncgil: " + e.getMessage()));
			counts[1] += assets.size();
			return;
		}
		for (var a : assets) {
			var price = prices.get(a.getSourceSymbol());
			if (price == null) {
				portfolio.markPriceError(a.getId(), "Truncgil'de \"" + a.getSourceSymbol() + "\" bulunamadı");
				counts[1]++;
			}
			else {
				portfolio.applyMarketPrice(a.getId(), price);
				counts[0]++;
			}
		}
	}

	private void refreshYahoo(Map<String, String> sources, int[] counts) {
		var assets = portfolio.assetsWithSource(PriceSource.YAHOO);
		if (assets.isEmpty()) {
			return;
		}
		List<String> errors = new ArrayList<>();
		for (var a : assets) {
			try {
				var q = client.yahoo(a.getSourceSymbol());
				if (q.currency() != null && !q.currency().equalsIgnoreCase(a.getPriceCurrency().name())) {
					throw new IllegalStateException(a.getSourceSymbol() + " fiyatı " + q.currency() + " cinsinden, varlık " + a.getPriceCurrency());
				}
				portfolio.applyMarketPrice(a.getId(), q.price());
				counts[0]++;
			}
			catch (RuntimeException e) {
				portfolio.markPriceError(a.getId(), "Yahoo: " + e.getMessage());
				errors.add(a.getSourceSymbol() + ": " + e.getMessage());
				counts[1]++;
			}
		}
		sources.put("Yahoo", errors.isEmpty() ? "ok" : String.join("; ", errors));
	}

	/** "≈" işaretli işlemlerin kurunu işlem gününün TCMB kuruyla düzeltir. */
	private int fixEstimatedRates(Map<String, String> sources) {
		var trades = portfolio.estimatedTrades();
		int fixed = 0;
		for (var t : trades.stream().limit(MAX_RATE_FIXES).toList()) {
			try {
				var rate = client.tcmbRate(Currency.USD, t.getDate());
				if (rate.isPresent()) {
					portfolio.fixEstimatedRate(t.getId(), rate.get().rate());
					fixed++;
				}
			}
			catch (RuntimeException e) {
				sources.put("TCMB arşiv", e.getMessage());
				break;
			}
		}
		return fixed;
	}
}
