package com.example.demo.finance;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.example.demo.finance.FinanceDtos.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinanceController {

	private final PortfolioService service;

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
		return service.createTrade(req);
	}

	@PutMapping("/trades/{id}")
	public TradeResponse updateTrade(@PathVariable Long id, @Valid @RequestBody TradeRequest req) {
		return service.updateTrade(id, req);
	}

	@DeleteMapping("/trades/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteTrade(@PathVariable Long id) {
		service.deleteTrade(id);
	}

	// --- Kurlar ---
	@GetMapping("/rates")
	public List<RateResponse> rates() {
		return service.listRates();
	}

	@PutMapping("/rates/{currency}")
	public RateResponse setRate(@PathVariable Currency currency, @Valid @RequestBody RateRequest req) {
		return service.setRate(currency, req.rateTry());
	}
}
