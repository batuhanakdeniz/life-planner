package com.example.demo.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class PaymentDtos {

	private PaymentDtos() {
	}

	public record PaymentRequest(
			@NotBlank @Size(max = 255) String name,
			@Size(max = 64) String category,
			@NotNull @Positive BigDecimal amount,
			@NotNull Currency currency,
			@NotNull Frequency frequency,
			@NotNull LocalDate nextDueDate,
			Boolean active,
			@Size(max = 500) String note) {
	}

	/** Ödeme yapıldı. Boş alanlar: bugün ve ödemenin kayıtlı tutarı. */
	public record PayRequest(LocalDate paidDate, @Positive BigDecimal amount) {
	}

	/**
	 * @param daysUntilDue    negatifse gecikmiş
	 * @param monthlyTry      aylık eşdeğerin TL karşılığı (kur yoksa null)
	 */
	public record PaymentResponse(Long id, String name, String category, BigDecimal amount, Currency currency,
			Frequency frequency, LocalDate nextDueDate, long daysUntilDue, boolean active, String note,
			BigDecimal monthlyTry, LocalDate lastPaidDate) {
	}

	public record PaymentLogResponse(Long id, Long paymentId, String paymentName, LocalDate dueDate, LocalDate paidDate,
			BigDecimal amount, Currency currency) {
	}

	/**
	 * @param monthlyTotalTry    aktif ödemelerin aylık toplam yükü (TL)
	 * @param missingRates       kuru girilmediği için toplama katılamayan para birimleri
	 * @param paidThisMonthTry   bu ay yapılan ödemelerin TL toplamı
	 */
	public record PaymentsOverview(BigDecimal monthlyTotalTry, List<Currency> missingRates, BigDecimal paidThisMonthTry,
			List<PaymentResponse> payments, List<PaymentLogResponse> recent) {
	}
}
