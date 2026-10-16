package com.example.demo.finance.market;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.demo.finance.AssetType;
import com.example.demo.finance.Currency;

class PriceSourceDefaultsTests {

	@Test
	void goldCoinsMapToTruncgilKeys() {
		var quarter = PriceSourceDefaults.suggest("ceyrek_altin", AssetType.GOLD, Currency.TRY);
		assertThat(quarter.source()).isEqualTo(PriceSource.TRUNCGIL);
		assertThat(quarter.sourceSymbol()).isEqualTo("ceyrek-altin");

		var ata = PriceSourceDefaults.suggest("ATA_LIRA", AssetType.GOLD, Currency.TRY);
		assertThat(ata.source()).isEqualTo(PriceSource.TRUNCGIL);
		assertThat(ata.sourceSymbol()).isEqualTo("ata-altin");
	}

	@Test
	void unknownGoldAndFundsStayManual() {
		assertThat(PriceSourceDefaults.suggest("BILEZIK", AssetType.GOLD, Currency.TRY).source()).isEqualTo(PriceSource.MANUAL);
		assertThat(PriceSourceDefaults.suggest("XYZ", AssetType.FUND, Currency.TRY).source()).isEqualTo(PriceSource.MANUAL);
	}

	@Test
	void stocksUseYahooWithBistSuffixForLiraPrices() {
		assertThat(PriceSourceDefaults.suggest("thyao", AssetType.STOCK, Currency.TRY).sourceSymbol()).isEqualTo("THYAO.IS");
		assertThat(PriceSourceDefaults.suggest("AAPL", AssetType.STOCK, Currency.USD).sourceSymbol()).isEqualTo("AAPL");
	}
}
