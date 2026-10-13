package com.example.demo.finance;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/**
 * Ortalama maliyet yöntemiyle pozisyon hesabı. Maliyet ve kâr/zarar hem TL hem USD olarak,
 * her işlemin kendi günündeki kurla izlenir; böylece "dolar bazında" kazanç da doğru çıkar.
 */
public final class PortfolioCalculator {

	static final MathContext MC = MathContext.DECIMAL64;

	private PortfolioCalculator() {
	}

	/** Bir varlığın tüm işlemleri işlendikten sonraki durumu. Tutarlar yuvarlanmamıştır. */
	public record Position(BigDecimal quantity, BigDecimal costTry, BigDecimal costUsd,
			BigDecimal realizedTry, BigDecimal realizedUsd, BigDecimal lastPrice, BigDecimal lastRate) {

		static final Position EMPTY = new Position(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
				BigDecimal.ZERO, BigDecimal.ZERO, null, null);
	}

	/** Bir işlemin TL ve USD cinsinden birim fiyatı. */
	record Prices(BigDecimal unitTry, BigDecimal unitUsd) {
	}

	static Prices prices(Currency priceCurrency, BigDecimal unitPrice, BigDecimal usdTry) {
		return priceCurrency == Currency.USD
				? new Prices(unitPrice.multiply(usdTry, MC), unitPrice)
				: new Prices(unitPrice, unitPrice.divide(usdTry, MC));
	}

	/**
	 * İşlemleri tarih sırasıyla uygular.
	 * @throws IllegalArgumentException herhangi bir anda eldekinden fazla satış yapılıyorsa
	 */
	public static Position position(Currency priceCurrency, List<Trade> tradesOldestFirst) {
		var p = Position.EMPTY;
		for (var t : tradesOldestFirst) {
			p = apply(p, priceCurrency, t);
		}
		return p;
	}

	static Position apply(Position p, Currency priceCurrency, Trade t) {
		var q = t.getQuantity();
		var fee = t.getFee() != null ? t.getFee() : BigDecimal.ZERO;
		var unit = prices(priceCurrency, t.getUnitPrice(), t.getUsdTryRate());
		var fees = prices(priceCurrency, fee, t.getUsdTryRate());

		if (t.getSide() == TradeSide.BUY) {
			// Alış masrafı maliyete eklenir
			return new Position(p.quantity().add(q),
					p.costTry().add(q.multiply(unit.unitTry(), MC)).add(fees.unitTry()),
					p.costUsd().add(q.multiply(unit.unitUsd(), MC)).add(fees.unitUsd()),
					p.realizedTry(), p.realizedUsd(), t.getUnitPrice(), t.getUsdTryRate());
		}

		if (q.compareTo(p.quantity()) > 0) {
			throw new IllegalArgumentException(t.getDate() + " tarihli satış (" + q.stripTrailingZeros().toPlainString()
					+ "), o tarihte eldeki miktarı (" + p.quantity().stripTrailingZeros().toPlainString() + ") aşıyor");
		}
		// Satılan kısmın maliyeti ortalama maliyetten düşülür; fark gerçekleşen kâr/zarardır
		var ratio = q.divide(p.quantity(), MC);
		var soldCostTry = p.costTry().multiply(ratio, MC);
		var soldCostUsd = p.costUsd().multiply(ratio, MC);
		var proceedsTry = q.multiply(unit.unitTry(), MC).subtract(fees.unitTry());
		var proceedsUsd = q.multiply(unit.unitUsd(), MC).subtract(fees.unitUsd());
		var remaining = p.quantity().subtract(q);
		boolean closed = remaining.signum() == 0;
		return new Position(remaining,
				closed ? BigDecimal.ZERO : p.costTry().subtract(soldCostTry),
				closed ? BigDecimal.ZERO : p.costUsd().subtract(soldCostUsd),
				p.realizedTry().add(proceedsTry.subtract(soldCostTry)),
				p.realizedUsd().add(proceedsUsd.subtract(soldCostUsd)),
				t.getUnitPrice(), t.getUsdTryRate());
	}

	static BigDecimal money(BigDecimal v) {
		return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
	}

	static BigDecimal percent(BigDecimal part, BigDecimal whole) {
		if (part == null || whole == null || whole.signum() == 0) {
			return null;
		}
		return part.multiply(BigDecimal.valueOf(100)).divide(whole, 2, RoundingMode.HALF_UP);
	}
}
