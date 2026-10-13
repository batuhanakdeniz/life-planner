package com.example.demo.finance;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class FinanceDtos {

	private FinanceDtos() {
	}

	// ---------- Varlıklar ----------
	public record AssetRequest(
			@NotBlank @Size(max = 32) @Pattern(regexp = "^[A-Za-z0-9_.\\-]+$", message = "harf, rakam, _ . - içerebilir") String symbol,
			@NotBlank @Size(max = 255) String name,
			@NotNull AssetType type,
			@NotNull Currency priceCurrency,
			@Size(max = 16) String unit,
			@Positive BigDecimal currentPrice) {
	}

	public record PriceRequest(@NotNull @Positive BigDecimal price) {
	}

	/** @param priceFromRate fiyat elle girilmiyor, kurlardan geliyor (USD, EUR varlıkları) */
	public record AssetResponse(Long id, String symbol, String name, AssetType type, Currency priceCurrency, String unit,
			BigDecimal currentPrice, Instant priceUpdatedAt, boolean priceFromRate, boolean hasTrades) {
	}

	// ---------- İşlemler ----------
	public record TradeRequest(
			@NotNull Long assetId,
			@NotNull TradeSide side,
			@NotNull LocalDate date,
			@NotNull @Positive BigDecimal quantity,
			@NotNull @Positive BigDecimal unitPrice,
			/** Boşsa güncel kur kullanılır ve işlem "tahmini kur" olarak işaretlenir. */
			@Positive BigDecimal usdTryRate,
			@PositiveOrZero BigDecimal fee,
			@Size(max = 500) String note) {
	}

	public record TradeResponse(Long id, Long assetId, String assetSymbol, String assetName, String unit,
			Currency priceCurrency, TradeSide side, LocalDate date, BigDecimal quantity, BigDecimal unitPrice,
			BigDecimal usdTryRate, boolean rateEstimated, BigDecimal fee, String note, BigDecimal totalTry, BigDecimal totalUsd) {
	}

	// ---------- Kurlar ----------
	public record RateRequest(@NotNull @DecimalMin(value = "0.000001") BigDecimal rateTry) {
	}

	public record RateResponse(Currency currency, BigDecimal rateTry, Instant updatedAt) {
	}

	// ---------- Portföy ----------
	/**
	 * @param priceIsFallback güncel fiyat girilmemiş; son işlem fiyatı kullanıldı
	 * @param closed          tamamı satılmış pozisyon (yalnızca gerçekleşen kâr/zarar için listelenir)
	 */
	public record Holding(Long assetId, String symbol, String name, AssetType type, Currency priceCurrency, String unit,
			BigDecimal quantity, BigDecimal avgCost, BigDecimal price, boolean priceIsFallback,
			BigDecimal costTry, BigDecimal costUsd, BigDecimal valueTry, BigDecimal valueUsd,
			BigDecimal unrealizedTry, BigDecimal unrealizedUsd, BigDecimal unrealizedPctTry, BigDecimal unrealizedPctUsd,
			BigDecimal realizedTry, BigDecimal realizedUsd, BigDecimal weight, boolean closed) {
	}

	public record Allocation(AssetType type, BigDecimal valueTry, BigDecimal valueUsd, BigDecimal percent) {
	}

	public record Totals(BigDecimal valueTry, BigDecimal valueUsd, BigDecimal costTry, BigDecimal costUsd,
			BigDecimal unrealizedTry, BigDecimal unrealizedUsd, BigDecimal unrealizedPctTry, BigDecimal unrealizedPctUsd,
			BigDecimal realizedTry, BigDecimal realizedUsd) {
	}

	/**
	 * @param usdTry         değerlemede kullanılan USD/TRY kuru
	 * @param rateIsFallback güncel kur girilmemiş; son işlemdeki kur kullanıldı
	 */
	public record Portfolio(BigDecimal usdTry, Instant usdTryUpdatedAt, boolean rateIsFallback, Totals totals,
			List<Allocation> allocation, List<Holding> holdings) {
	}
}
