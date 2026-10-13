package com.example.demo.finance.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.example.demo.finance.Currency;

/** Gerçek yanıtlardan (13 Ekim 2026) kısaltılmış örneklerle. */
class MarketParsersTests {

	@Test
	void truncgilUsesBuyingPriceAndTurkishNumbers() {
		var json = """
				{"Update_Date":"2026-10-13 20:30:03",
				 "USD":{"Alış":"49,3592","Tür":"Döviz","Satış":"49,3701","Değişim":"%0,12"},
				 "gram-altin":{"Alış":"6.591,46","Tür":"Altın","Satış":"6.592,43","Değişim":"%-0,31"},
				 "ceyrek-altin":{"Alış":"10.547,41","Tür":"Altın","Satış":"10.788,42","Değişim":"%0,03"},
				 "ons":{"Alış":"$4.153,12","Tür":"Altın","Satış":"$4.153,73","Değişim":"%-0,43"}}
				""";
		var prices = MarketParsers.truncgil(json);
		assertThat(prices.get("gram-altin")).isEqualByComparingTo("6591.46");
		assertThat(prices.get("ceyrek-altin")).isEqualByComparingTo("10547.41");
		assertThat(prices.get("ons")).isEqualByComparingTo("4153.12");
		assertThat(prices).doesNotContainKey("Update_Date");
	}

	@Test
	void tcmbReadsForexBuyingPerUnit() {
		var xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<Tarih_Date Tarih="13.10.2026" Date="10/13/2026" Bulten_No="2026/193">
				  <Currency CrossOrder="0" Kod="USD" CurrencyCode="USD"><Unit>1</Unit><ForexBuying>49.2794</ForexBuying></Currency>
				  <Currency CrossOrder="9" Kod="EUR" CurrencyCode="EUR"><Unit>1</Unit><ForexBuying>55.1001</ForexBuying></Currency>
				  <Currency CrossOrder="1" Kod="JPY" CurrencyCode="JPY"><Unit>100</Unit><ForexBuying>32.1</ForexBuying></Currency>
				</Tarih_Date>
				""";
		var rates = MarketParsers.tcmb(xml);
		assertThat(rates.get(Currency.USD)).isEqualByComparingTo("49.2794");
		assertThat(rates.get(Currency.EUR)).isEqualByComparingTo("55.1001");
		assertThat(rates).doesNotContainKey(Currency.TRY);
	}

	@Test
	void yahooReadsRegularMarketPrice() {
		var json = """
				{"chart":{"result":[{"meta":{"currency":"TRY","symbol":"THYAO.IS","regularMarketPrice":286.25}}],"error":null}}
				""";
		var q = MarketParsers.yahoo(json);
		assertThat(q.price()).isEqualByComparingTo("286.25");
		assertThat(q.currency()).isEqualTo("TRY");
	}

	@Test
	void yahooUnknownSymbolIsAnError() {
		var json = """
				{"chart":{"result":null,"error":{"code":"Not Found","description":"No data found, symbol may be delisted"}}}
				""";
		assertThatThrownBy(() -> MarketParsers.yahoo(json)).hasMessageContaining("delisted");
	}
}
