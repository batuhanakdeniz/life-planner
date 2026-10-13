package com.example.demo.finance.market;

/** Bir varlığın güncel fiyatının nereden geldiği. */
public enum PriceSource {
	/** Fiyat elle girilir. */
	MANUAL,
	/** Döviz varlığı (USD, EUR); fiyatı TCMB kurudur. */
	RATE,
	/** finans.truncgil.com — Türkiye piyasası altın/gümüş fiyatları. */
	TRUNCGIL,
	/** Yahoo Finance — BIST (THYAO.IS) ve ABD hisseleri. */
	YAHOO
}
