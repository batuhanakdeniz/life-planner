package com.example.demo.finance;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.example.demo.finance.FinanceDtos.*;
import com.example.demo.finance.market.MarketDataClient;
import com.example.demo.finance.market.PriceUpdateService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinanceController {

	private final PortfolioService service;
	private final PriceUpdateService prices;
	private final MarketDataClient market;

	@GetMapping("/portfolio")
	public Portfolio portfolio() {
		return service.portfolio();
	}

	// --- Varlıklar ---
	@GetMapping("/assets")
	public List<AssetResponse> assets() {
		return service.listAssets();
	}

	@PostMapping("/assets")
	@ResponseStatus(HttpStatus.CREATED)
	public AssetResponse createAsset(@Valid @RequestBody AssetRequest req) {
		return service.createAsset(req);
	}

	@PutMapping("/assets/{id}")
	public AssetResponse updateAsset(@PathVariable Long id, @Valid @RequestBody AssetRequest req) {
		return service.updateAsset(id, req);
	}

	/** Güncel fiyatı günceller. İleride otomatik fiyat kaynağı da bu yolu kullanacak. */
	@PutMapping("/assets/{id}/price")
	public AssetResponse setPrice(@PathVariable Long id, @Valid @RequestBody PriceRequest req) {
		return service.setPrice(id, req.price());
	}

	@PutMapping("/assets/{id}/source")
	public AssetResponse setSource(@PathVariable Long id, @Valid @RequestBody SourceRequest req) {
		return service.updateSource(id, req.priceSource(), req.sourceSymbol());
	}

	@DeleteMapping("/assets/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteAsset(@PathVariable Long id) {
		service.deleteAsset(id);
	}

	// --- İşlemler ---
	@GetMapping("/trades")
	public List<TradeResponse> trades(@RequestParam(required = false) Long assetId) {
		return service.listTrades(assetId);
	}

	@PostMapping("/trades")
	@ResponseStatus(HttpStatus.CREATED)
	public TradeResponse createTrade(@Valid @RequestBody TradeRequest req) {
		return service.createTrade(withHistoricalRate(req));
	}

	@PutMapping("/trades/{id}")
	public TradeResponse updateTrade(@PathVariable Long id, @Valid @RequestBody TradeRequest req) {
		return service.updateTrade(id, withHistoricalRate(req));
	}

	/** Kur boşsa işlem gününün TCMB kurunu doldurur; TCMB'ye ulaşılamazsa güncel kurla tahmini kayıt yapılır. */
	private TradeRequest withHistoricalRate(TradeRequest req) {
		if (req.usdTryRate() != null) {
			return req;
		}
		try {
			return market.tcmbRate(Currency.USD, req.date())
					.map(r -> new TradeRequest(req.assetId(), req.side(), req.date(), req.quantity(), req.unitPrice(),
							r.rate(), req.fee(), req.note()))
					.orElse(req);
		}
		catch (RuntimeException e) {
			return req;
		}
	}

	@DeleteMapping("/trades/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteTrade(@PathVariable Long id) {
		service.deleteTrade(id);
	}

	// --- Otomatik fiyatlar ---
	@GetMapping("/prices/status")
	public ResponseEntity<PriceUpdateService.RefreshStatus> priceStatus() {
		var status = prices.lastStatus();
		return status != null ? ResponseEntity.ok(status) : ResponseEntity.noContent().build();
	}

	@PostMapping("/prices/refresh")
	public PriceUpdateService.RefreshStatus refreshPrices() {
		return prices.refreshAll();
	}

	// --- Kurlar ---
	@GetMapping("/rates")
	public List<RateResponse> rates() {
		return service.listRates();
	}

	/** GET /api/finance/rates/history?date=2025-11-05 → o günün TCMB USD alış kuru */
	@GetMapping("/rates/history")
	public ResponseEntity<HistoricalRate> historicalRate(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(defaultValue = "USD") Currency currency) {
		return ResponseEntity.of(market.tcmbRate(currency, date)
				.map(r -> new HistoricalRate(currency, date, r.date(), r.rate())));
	}

	@PutMapping("/rates/{currency}")
	public RateResponse setRate(@PathVariable Currency currency, @Valid @RequestBody RateRequest req) {
		return service.setRate(currency, req.rateTry());
	}
}
