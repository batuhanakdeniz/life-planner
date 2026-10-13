package com.example.demo.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class PortfolioCalculatorTests {

	private static Trade trade(TradeSide side, String date, String qty, String price, String rate, String fee) {
		var t = new Trade();
		t.setSide(side);
		t.setDate(LocalDate.parse(date));
		t.setQuantity(new BigDecimal(qty));
		t.setUnitPrice(new BigDecimal(price));
		t.setUsdTryRate(new BigDecimal(rate));
		t.setFee(fee != null ? new BigDecimal(fee) : null);
		return t;
	}

	private static void assertMoney(BigDecimal actual, String expected) {
		assertThat(PortfolioCalculator.money(actual)).isEqualByComparingTo(expected);
	}

	@Test
	void averageCostAndRealizedProfitInBothCurrencies() {
		// Gram altın (TRY fiyatlı): iki alış, bir kısmi satış
		var p = PortfolioCalculator.position(Currency.TRY, List.of(
				trade(TradeSide.BUY, "2026-01-10", "10", "3000", "30", null),   // 30.000 TL = 1.000 $
				trade(TradeSide.BUY, "2026-03-10", "10", "3300", "33", null),   // 33.000 TL = 1.000 $
				trade(TradeSide.SELL, "2026-06-10", "5", "3600", "36", null))); // 18.000 TL = 500 $

		assertThat(p.quantity()).isEqualByComparingTo("15");
		// Satılan çeyreğin maliyeti: 15.750 TL / 500 $
		assertMoney(p.costTry(), "47250");
		assertMoney(p.costUsd(), "1500");
		// TL bazında 2.250 kâr, ama dolar bazında kâr yok (TL değer kaybı)
		assertMoney(p.realizedTry(), "2250");
		assertMoney(p.realizedUsd(), "0");
	}

	@Test
	void usdPricedAssetCostsConvertWithTradeDayRate() {
		var p = PortfolioCalculator.position(Currency.USD, List.of(
				trade(TradeSide.BUY, "2026-01-10", "2", "200", "30", "1")));
		assertMoney(p.costUsd(), "401");
		assertMoney(p.costTry(), "12030");
	}

	@Test
	void sellFeeReducesProceedsAndFullSellClosesPosition() {
		var p = PortfolioCalculator.position(Currency.TRY, List.of(
				trade(TradeSide.BUY, "2026-01-10", "100", "10", "30", null),
				trade(TradeSide.SELL, "2026-02-10", "100", "12", "30", "20")));
		assertThat(p.quantity().signum()).isZero();
		assertMoney(p.costTry(), "0");
		assertMoney(p.realizedTry(), "180"); // 1200 - 20 masraf - 1000 maliyet
	}

	@Test
	void sellingMoreThanHeldIsRejected() {
		assertThatThrownBy(() -> PortfolioCalculator.position(Currency.TRY, List.of(
				trade(TradeSide.BUY, "2026-01-10", "5", "10", "30", null),
				trade(TradeSide.SELL, "2026-01-11", "6", "10", "30", null))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("aşıyor");
	}

	@Test
	void currencyAssetIsPricedFromExchangeRate() {
		var usd = new Asset();
		usd.setSymbol("USD");
		usd.setType(AssetType.CURRENCY);
		usd.setPriceCurrency(Currency.TRY);
		usd.setCurrentPrice(new BigDecimal("40")); // eski elle girilmiş fiyat yok sayılır
		var rates = java.util.Map.of(Currency.USD, new BigDecimal("45.2"));
		assertThat(PortfolioService.currentPrice(usd, rates)).isEqualByComparingTo("45.2");

		var gold = new Asset();
		gold.setSymbol("GRAM_ALTIN");
		gold.setType(AssetType.GOLD);
		gold.setPriceCurrency(Currency.TRY);
		gold.setCurrentPrice(new BigDecimal("6300"));
		assertThat(PortfolioService.currentPrice(gold, rates)).isEqualByComparingTo("6300");
	}

	@Test
	void plainNeverUsesScientificNotation() {
		assertThat(PortfolioService.plain(new BigDecimal("3000.00000000")).toString()).isEqualTo("3000");
		assertThat(PortfolioService.plain(new BigDecimal("2.50000000")).toString()).isEqualTo("2.5");
	}
}
