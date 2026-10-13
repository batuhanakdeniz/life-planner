package com.example.demo.finance;

import static com.example.demo.finance.PortfolioCalculator.MC;
import static com.example.demo.finance.PortfolioCalculator.money;
import static com.example.demo.finance.PortfolioCalculator.percent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.common.NotFoundException;
import com.example.demo.finance.FinanceDtos.*;
import com.example.demo.finance.market.PriceSource;
import com.example.demo.finance.market.PriceSourceDefaults;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class PortfolioService {

	private final AssetRepository assets;
	private final TradeRepository trades;
	private final ExchangeRateRepository rates;

	// ---------- Varlıklar ----------
	@Transactional(readOnly = true)
	public List<AssetResponse> listAssets() {
		return assets.findAllByOrderByTypeAscNameAsc().stream().map(this::toResponse).toList();
	}

	public AssetResponse createAsset(AssetRequest req) {
		if (assets.existsBySymbolIgnoreCase(req.symbol())) {
			throw new IllegalArgumentException("Bu sembolle bir varlık zaten var: " + req.symbol());
		}
		var asset = new Asset();
		apply(asset, req);
		return toResponse(assets.save(asset));
	}

	public AssetResponse updateAsset(Long id, AssetRequest req) {
		var asset = getAsset(id);
		if (asset.getPriceCurrency() != req.priceCurrency() && trades.existsByAssetId(id)) {
			throw new IllegalArgumentException("İşlemi olan bir varlığın fiyat para birimi değiştirilemez");
		}
		if (!asset.getSymbol().equalsIgnoreCase(req.symbol()) && assets.existsBySymbolIgnoreCase(req.symbol())) {
			throw new IllegalArgumentException("Bu sembolle bir varlık zaten var: " + req.symbol());
		}
		apply(asset, req);
		return toResponse(asset);
	}

	public AssetResponse setPrice(Long id, BigDecimal price) {
		var asset = getAsset(id);
		asset.setCurrentPrice(price);
		asset.setPriceUpdatedAt(Instant.now());
		return toResponse(asset);
	}

	public AssetResponse updateSource(Long id, PriceSource source, String sourceSymbol) {
		var asset = getAsset(id);
		setSource(asset, source, sourceSymbol);
		return toResponse(asset);
	}

	private static void setSource(Asset asset, PriceSource source, String sourceSymbol) {
		if ((source == PriceSource.TRUNCGIL || source == PriceSource.YAHOO) && (sourceSymbol == null || sourceSymbol.isBlank())) {
			throw new IllegalArgumentException(source + " kaynağı için kaynak sembolü gerekli (ör. gram-altin, THYAO.IS)");
		}
		asset.setPriceSource(source);
		asset.setSourceSymbol(sourceSymbol != null && !sourceSymbol.isBlank() ? sourceSymbol.trim() : null);
		asset.setPriceError(null);
	}

	/** Eski kayıtlara (kaynak alanı eklenmeden önce oluşturulmuş) önerilen kaynağı atar. */
	public int assignMissingSources() {
		int n = 0;
		for (var a : assets.findAll()) {
			if (a.getPriceSource() == null) {
				var s = PriceSourceDefaults.suggest(a.getSymbol(), a.getType(), a.getPriceCurrency());
				setSource(a, s.source(), s.sourceSymbol());
				n++;
			}
		}
		return n;
	}

	/** Otomatik güncellemeden gelen fiyat. */
	public void applyMarketPrice(Long assetId, BigDecimal price) {
		var asset = getAsset(assetId);
		asset.setCurrentPrice(price);
		asset.setPriceUpdatedAt(Instant.now());
		asset.setPriceError(null);
	}

	public void markPriceError(Long assetId, String error) {
		getAsset(assetId).setPriceError(error.length() > 300 ? error.substring(0, 300) : error);
	}

	@Transactional(readOnly = true)
	public List<Asset> assetsWithSource(PriceSource source) {
		return assets.findAll().stream().filter(a -> a.getPriceSource() == source).toList();
	}

	@Transactional(readOnly = true)
	public List<Trade> estimatedTrades() {
		return trades.findByRateEstimatedTrue();
	}

	/** Tahmini kuru, işlem gününün gerçek TCMB kuruyla değiştirir. */
	public void fixEstimatedRate(Long tradeId, BigDecimal rate) {
		trades.findById(tradeId).ifPresent(t -> {
			t.setUsdTryRate(rate);
			t.setRateEstimated(false);
		});
	}

	public void deleteAsset(Long id) {
		if (trades.existsByAssetId(id)) {
			throw new IllegalArgumentException("Bu varlığa ait işlemler var; önce işlemleri silmelisin");
		}
		assets.delete(getAsset(id));
	}

	private void apply(Asset asset, AssetRequest req) {
		if (req.priceCurrency() == Currency.EUR) {
			throw new IllegalArgumentException("Varlık fiyatı TRY veya USD cinsinden olmalı");
		}
		asset.setSymbol(req.symbol().toUpperCase(java.util.Locale.ROOT));
		asset.setName(req.name());
		asset.setType(req.type());
		asset.setPriceCurrency(req.priceCurrency());
		asset.setUnit(req.unit());
		if (req.priceSource() != null) {
			setSource(asset, req.priceSource(), req.sourceSymbol());
		}
		else if (asset.getPriceSource() == null) {
			var s = PriceSourceDefaults.suggest(asset.getSymbol(), asset.getType(), asset.getPriceCurrency());
			setSource(asset, s.source(), s.sourceSymbol());
		}
		if (req.currentPrice() != null && !req.currentPrice().equals(asset.getCurrentPrice())) {
			asset.setCurrentPrice(req.currentPrice());
			asset.setPriceUpdatedAt(Instant.now());
		}
	}

	private AssetResponse toResponse(Asset a) {
		Map<Currency, BigDecimal> rateMap = new EnumMap<>(Currency.class);
		rates.findAll().forEach(r -> rateMap.put(r.getCurrency(), r.getRateTry()));
		var fromRate = currentPrice(a, rateMap) != a.getCurrentPrice();
		return new AssetResponse(a.getId(), a.getSymbol(), a.getName(), a.getType(), a.getPriceCurrency(), a.getUnit(),
				currentPrice(a, rateMap), fromRate ? rateUpdatedAt(a) : a.getPriceUpdatedAt(), fromRate, trades.existsByAssetId(a.getId()),
				a.getPriceSource(), a.getSourceSymbol(), a.getPriceError());
	}

	private Instant rateUpdatedAt(Asset a) {
		try {
			return rates.findById(Currency.valueOf(a.getSymbol())).map(ExchangeRate::getUpdatedAt).orElse(null);
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}

	private Asset getAsset(Long id) {
		return assets.findById(id).orElseThrow(() -> new NotFoundException("Varlık", id));
	}

	// ---------- İşlemler ----------
	@Transactional(readOnly = true)
	public List<TradeResponse> listTrades(Long assetId) {
		var list = assetId != null ? trades.findByAssetIdOrderByDateDescIdDesc(assetId) : trades.findAllByOrderByDateDescIdDesc();
		return list.stream().map(PortfolioService::toResponse).toList();
	}

	public TradeResponse createTrade(TradeRequest req) {
		var trade = new Trade();
		apply(trade, req);
		validate(trade.getAsset(), trade);
		return toResponse(trades.save(trade));
	}

	public TradeResponse updateTrade(Long id, TradeRequest req) {
		var trade = trades.findById(id).orElseThrow(() -> new NotFoundException("İşlem", id));
		var oldAsset = trade.getAsset();
		apply(trade, req);
		validate(trade.getAsset(), trade);
		if (!oldAsset.getId().equals(trade.getAsset().getId())) {
			validate(oldAsset, null);
		}
		return toResponse(trade);
	}

	public void deleteTrade(Long id) {
		var trade = trades.findById(id).orElseThrow(() -> new NotFoundException("İşlem", id));
		trades.delete(trade);
		trades.flush();
		// Bir alış silinince sonraki satışlar eldekini aşabilir
		validate(trade.getAsset(), null);
	}

	/** Değişiklik sonrası varlığın tüm işlem geçmişinin tutarlı olduğunu (açığa satış olmadığını) doğrular. */
	private void validate(Asset asset, Trade changed) {
		var list = new ArrayList<>(trades.findByAssetIdOrderByDateAscIdAsc(asset.getId()));
		if (changed != null && list.stream().noneMatch(t -> t == changed)) {
			list.removeIf(t -> t.getId() != null && t.getId().equals(changed.getId()));
			list.add(changed);
		}
		list.sort(Comparator.comparing(Trade::getDate)
				.thenComparing(t -> t.getSide() == TradeSide.SELL) // aynı gün önce alışlar
				.thenComparing(t -> t.getId() == null ? Long.MAX_VALUE : t.getId()));
		PortfolioCalculator.position(asset.getPriceCurrency(), list);
	}

	private void apply(Trade trade, TradeRequest req) {
		trade.setAsset(getAsset(req.assetId()));
		trade.setSide(req.side());
		trade.setDate(req.date());
		trade.setQuantity(req.quantity());
		trade.setUnitPrice(req.unitPrice());
		if (req.usdTryRate() != null) {
			trade.setUsdTryRate(req.usdTryRate());
			trade.setRateEstimated(false);
		}
		else if (trade.getUsdTryRate() == null || trade.isRateEstimated()) {
			// Kur bilinmiyor: güncel kurla kaydet, tahmini olarak işaretle
			trade.setUsdTryRate(rates.findById(Currency.USD).map(ExchangeRate::getRateTry).orElseThrow(
					() -> new IllegalArgumentException("İşlem kuru boş bırakılacaksa önce Portföy'de güncel USD/TRY kurunu gir")));
			trade.setRateEstimated(true);
		}
		trade.setFee(req.fee());
		trade.setNote(req.note());
	}

	private static TradeResponse toResponse(Trade t) {
		var a = t.getAsset();
		var gross = t.getQuantity().multiply(t.getUnitPrice(), MC);
		var fee = t.getFee() != null ? t.getFee() : BigDecimal.ZERO;
		var total = t.getSide() == TradeSide.BUY ? gross.add(fee) : gross.subtract(fee);
		var p = PortfolioCalculator.prices(a.getPriceCurrency(), total, t.getUsdTryRate());
		return new TradeResponse(t.getId(), a.getId(), a.getSymbol(), a.getName(), a.getUnit(), a.getPriceCurrency(),
				t.getSide(), t.getDate(), t.getQuantity(), t.getUnitPrice(), t.getUsdTryRate(), t.isRateEstimated(), t.getFee(), t.getNote(),
				money(p.unitTry()), money(p.unitUsd()));
	}

	// ---------- Kurlar ----------
	@Transactional(readOnly = true)
	public List<RateResponse> listRates() {
		return rates.findAll().stream().map(r -> new RateResponse(r.getCurrency(), r.getRateTry(), r.getUpdatedAt())).toList();
	}

	public RateResponse setRate(Currency currency, BigDecimal rateTry) {
		if (currency == Currency.TRY) {
			throw new IllegalArgumentException("TRY için kur girilmez");
		}
		var rate = rates.findById(currency).orElseGet(() -> new ExchangeRate(currency, rateTry));
		rate.setRateTry(rateTry);
		rate.setUpdatedAt(Instant.now());
		rates.save(rate);
		return new RateResponse(currency, rateTry, rate.getUpdatedAt());
	}

	/** Kur girilmemişse en son işlemdeki USD/TRY kuru kullanılır. */
	@Transactional(readOnly = true)
	public java.util.Optional<BigDecimal> rateTry(Currency currency) {
		if (currency == Currency.TRY) {
			return java.util.Optional.of(BigDecimal.ONE);
		}
		return rates.findById(currency).map(ExchangeRate::getRateTry);
	}

	// ---------- Portföy ----------
	@Transactional(readOnly = true)
	public Portfolio portfolio() {
		var all = trades.findAllByOrderByDateDescIdDesc();
		var usdRate = rates.findById(Currency.USD);
		boolean fallback = usdRate.isEmpty();
		BigDecimal usdTry = usdRate.map(ExchangeRate::getRateTry)
				.orElseGet(() -> all.isEmpty() ? null : all.get(0).getUsdTryRate());

		Map<Currency, BigDecimal> rateMap = new EnumMap<>(Currency.class);
		Map<Currency, Instant> rateTimes = new EnumMap<>(Currency.class);
		rates.findAll().forEach(r -> {
			rateMap.put(r.getCurrency(), r.getRateTry());
			rateTimes.put(r.getCurrency(), r.getUpdatedAt());
		});
		if (usdTry != null) {
			rateMap.putIfAbsent(Currency.USD, usdTry);
		}

		Map<Asset, List<Trade>> byAsset = all.stream().collect(Collectors.groupingBy(Trade::getAsset));
		List<Holding> holdings = new ArrayList<>();
		for (var entry : byAsset.entrySet()) {
			var asset = entry.getKey();
			var list = entry.getValue().stream()
					.sorted(Comparator.comparing(Trade::getDate).thenComparing(t -> t.getSide() == TradeSide.SELL).thenComparing(Trade::getId))
					.toList();
			holdings.add(holding(asset, PortfolioCalculator.position(asset.getPriceCurrency(), list), usdTry, rateMap, rateTimes));
		}

		var open = holdings.stream().filter(h -> !h.closed()).toList();
		var valueTry = sum(open, Holding::valueTry);
		var valueUsd = sum(open, Holding::valueUsd);
		var costTry = sum(open, Holding::costTry);
		var costUsd = sum(open, Holding::costUsd);
		var totals = new Totals(valueTry, valueUsd, costTry, costUsd,
				valueTry.subtract(costTry), valueUsd.subtract(costUsd),
				percent(valueTry.subtract(costTry), costTry), percent(valueUsd.subtract(costUsd), costUsd),
				sum(holdings, Holding::realizedTry), sum(holdings, Holding::realizedUsd));

		Map<AssetType, BigDecimal[]> byType = new EnumMap<>(AssetType.class);
		open.forEach(h -> {
			var acc = byType.computeIfAbsent(h.type(), k -> new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
			acc[0] = acc[0].add(h.valueTry());
			acc[1] = acc[1].add(h.valueUsd());
		});
		var allocation = byType.entrySet().stream()
				.map(e -> new Allocation(e.getKey(), e.getValue()[0], e.getValue()[1], percent(e.getValue()[0], valueTry)))
				.sorted(Comparator.comparing(Allocation::valueTry).reversed())
				.toList();

		var withWeights = holdings.stream()
				.map(h -> new Holding(h.assetId(), h.symbol(), h.name(), h.type(), h.priceCurrency(), h.unit(), h.quantity(),
						h.avgCost(), h.price(), h.priceIsFallback(), h.priceSource(), h.priceUpdatedAt(), h.priceError(), h.costTry(), h.costUsd(), h.valueTry(), h.valueUsd(),
						h.unrealizedTry(), h.unrealizedUsd(), h.unrealizedPctTry(), h.unrealizedPctUsd(),
						h.realizedTry(), h.realizedUsd(), h.closed() ? null : percent(h.valueTry(), valueTry), h.closed()))
				.sorted(Comparator.comparing(Holding::closed).thenComparing(Holding::valueTry, Comparator.reverseOrder()))
				.toList();

		return new Portfolio(usdTry, usdRate.map(ExchangeRate::getUpdatedAt).orElse(null), fallback && usdTry != null,
				totals, allocation, withWeights);
	}

	/**
	 * Döviz varlıklarının (sembolü USD/EUR, TL fiyatlı) fiyatı doğrudan güncel kurdur.
	 * Diğerleri için elle girilen fiyat, o da yoksa son işlem fiyatı kullanılır.
	 */
	static BigDecimal currentPrice(Asset a, Map<Currency, BigDecimal> rates) {
		if (a.getType() == AssetType.CURRENCY && a.getPriceCurrency() == Currency.TRY) {
			for (var c : Currency.values()) {
				if (c != Currency.TRY && c.name().equalsIgnoreCase(a.getSymbol()) && rates.containsKey(c)) {
					return rates.get(c);
				}
			}
		}
		return a.getCurrentPrice();
	}

	private static Holding holding(Asset a, PortfolioCalculator.Position p, BigDecimal usdTry, Map<Currency, BigDecimal> rates,
			Map<Currency, Instant> rateTimes) {
		boolean closed = p.quantity().signum() == 0;
		var current = currentPrice(a, rates);
		var updatedAt = current != null && current != a.getCurrentPrice()
				? rateTimes.get(Currency.valueOf(a.getSymbol().toUpperCase(java.util.Locale.ROOT))) : a.getPriceUpdatedAt();
		boolean fallback = current == null;
		var price = fallback ? p.lastPrice() : current;
		var value = PortfolioCalculator.prices(a.getPriceCurrency(), price, usdTry);
		var valueTry = p.quantity().multiply(value.unitTry(), MC);
		var valueUsd = p.quantity().multiply(value.unitUsd(), MC);
		// Ortalama maliyet, varlığın kendi fiyat para biriminde
		var cost = a.getPriceCurrency() == Currency.USD ? p.costUsd() : p.costTry();
		var avgCost = closed ? null : cost.divide(p.quantity(), MC).setScale(4, RoundingMode.HALF_UP);
		var unrealizedTry = valueTry.subtract(p.costTry());
		var unrealizedUsd = valueUsd.subtract(p.costUsd());
		return new Holding(a.getId(), a.getSymbol(), a.getName(), a.getType(), a.getPriceCurrency(), a.getUnit(),
				plain(p.quantity()), avgCost, price, fallback, a.getPriceSource(), updatedAt, a.getPriceError(),
				money(p.costTry()), money(p.costUsd()), money(valueTry), money(valueUsd),
				money(unrealizedTry), money(unrealizedUsd),
				percent(unrealizedTry, p.costTry()), percent(unrealizedUsd, p.costUsd()),
				money(p.realizedTry()), money(p.realizedUsd()), null, closed);
	}

	/** Gereksiz sıfırları atar ama 3E+3 gibi bilimsel gösterime düşmez. */
	static BigDecimal plain(BigDecimal v) {
		var s = v.stripTrailingZeros();
		return s.scale() < 0 ? s.setScale(0) : s;
	}

	private static BigDecimal sum(List<Holding> list, java.util.function.Function<Holding, BigDecimal> f) {
		return list.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
	}
}
