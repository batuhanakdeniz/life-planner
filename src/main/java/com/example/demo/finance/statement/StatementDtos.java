package com.example.demo.finance.statement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.example.demo.finance.statement.ParsedStatement.Kind;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class StatementDtos {

	private StatementDtos() {
	}

	/**
	 * @param spending       harcamalar toplamı (ödemeler hariç, iadeler düşülmüş)
	 * @param duplicate      aynı dosya daha önce yüklenmiş; mevcut kayıt döndü
	 */
	public record StatementSummary(Long id, String bank, boolean experimental, String cardLast4, LocalDate periodEnd,
			LocalDate dueDate, BigDecimal totalDue, BigDecimal spending, BigDecimal payments, int transactionCount,
			int unclassifiedCount, String fileName, Long fileId, Instant uploadedAt, boolean duplicate) {
	}

	/** Okunan toplamın ekstredeki dönem borcuyla karşılaştırması. */
	public record TotalsCheck(BigDecimal parsed, BigDecimal onStatement, boolean matches) {
	}

	/** categoryId null ise "Sınıflandırılmamış". */
	public record CategoryShare(Long categoryId, String name, String color, BigDecimal amount, BigDecimal percent, int count) {
	}

	/**
	 * @param remainingInstallments bu ekstreden sonra kalan taksit sayısı
	 * @param endsIn                son taksitin yansıyacağı ay (yaklaşık)
	 */
	public record InstallmentView(Long transactionId, String merchant, LocalDate purchaseDate, int installmentNo,
			int installmentCount, BigDecimal monthly, BigDecimal purchaseTotal, BigDecimal remainingAmount,
			int remainingInstallments, LocalDate endsIn, Long categoryId) {
	}

	/** @param nextMonthTotal önümüzdeki ekstreye yansıyacak taksitlerin toplamı */
	public record Installments(List<InstallmentView> items, BigDecimal nextMonthTotal, BigDecimal remainingTotal, int active) {
	}

	public record TransactionView(Long id, LocalDate date, String merchant, String description, BigDecimal amount, Kind kind,
			Integer installmentNo, Integer installmentCount, BigDecimal foreignAmount, String foreignCurrency, Long categoryId,
			String categorySource, String rulePattern, String suggestedPattern) {
	}

	public record StatementDetail(StatementSummary summary, List<String> warnings, TotalsCheck check,
			List<CategoryShare> breakdown, Installments installments, List<TransactionView> transactions) {
	}

	public record CategoryView(Long id, String name, String color, long transactionCount) {
	}

	public record CategoryRequest(@NotBlank @Size(max = 64) String name,
			@Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "#RRGGBB formatında olmalı") String color) {
	}

	/**
	 * İşlemin sınıfını değiştirir.
	 *
	 * @param categoryId null ise sınıf kaldırılır
	 * @param remember   true ise {@code pattern} içeren tüm işlemler için kural kaydedilir
	 */
	public record SetCategoryRequest(Long categoryId, boolean remember, @Size(max = 128) String pattern) {
	}

	public record RuleView(Long id, String pattern, Long categoryId, String categoryName, long matches) {
	}

	public record RuleRequest(@NotBlank @Size(max = 128) String pattern, Long categoryId) {
	}
}
