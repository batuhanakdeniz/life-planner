package com.example.demo.finance.statement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Bir ekstreden okunan ham bilgiler (veritabanından bağımsız).
 *
 * @param experimental okuyucu gerçek bir örnekle doğrulanmadı; sonuçlar kontrol edilmeli
 * @param warnings     okunamayan/şüpheli satırlar
 */
public record ParsedStatement(String bank, boolean experimental, String cardLast4, LocalDate periodEnd, LocalDate dueDate,
		BigDecimal totalDue, BigDecimal remainingInstallmentsTotal, List<Line> lines, List<String> warnings) {

	public enum Kind { PURCHASE, INSTALLMENT, PAYMENT, REFUND, FEE }

	/**
	 * Bir işlem satırı.
	 *
	 * @param amount                bu ekstreye yansıyan tutar (TL); ödeme ve iadelerde negatif
	 * @param installmentNo         taksitliyse kaçıncı taksit
	 * @param installmentCount      toplam taksit sayısı
	 * @param purchaseTotal         taksitli işlemin toplam tutarı
	 * @param remainingAmount       bu ekstreden sonra kalan tutar
	 * @param remainingInstallments bu ekstreden sonra kalan taksit sayısı
	 */
	public record Line(LocalDate date, String description, BigDecimal amount, Kind kind, Integer installmentNo,
			Integer installmentCount, BigDecimal purchaseTotal, BigDecimal remainingAmount, Integer remainingInstallments,
			BigDecimal foreignAmount, String foreignCurrency) {

		Line withInstallment(int no, int count, BigDecimal total) {
			return new Line(date, description, amount, Kind.INSTALLMENT, no, count, total, remainingAmount,
					remainingInstallments, foreignAmount, foreignCurrency);
		}

		Line withForeign(BigDecimal value, String currency) {
			return new Line(date, description, amount, kind, installmentNo, installmentCount, purchaseTotal, remainingAmount,
					remainingInstallments, value, currency);
		}
	}
}
