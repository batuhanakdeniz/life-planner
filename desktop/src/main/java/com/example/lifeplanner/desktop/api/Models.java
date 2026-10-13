package com.example.lifeplanner.desktop.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Backend REST API'sinin döndürdüğü JSON yapılarının karşılıkları. */
public final class Models {

	private Models() {
	}

	public record Task(Long id, String title, String description, LocalDate dueDate, String priority, String status,
			boolean completed, Long goalId, String goalTitle, boolean overdue) {
	}

	public record DayStatus(LocalDate date, boolean scheduled, boolean done) {
	}

	public record Habit(Long id, String name, String description, String icon, String color, List<String> days,
			boolean archived, LocalDate startDate, boolean scheduled, boolean doneOnDate, int currentStreak,
			int longestStreak, List<DayStatus> last7Days) {
	}

	public record Milestone(Long id, String title, boolean done) {
	}

	public record Goal(Long id, String title, String description, String category, String status,
			LocalDate targetDate, LocalDate startDate, int progress, List<Milestone> milestones) {
	}

	public record JournalEntry(Long id, LocalDate date, String content, Integer mood) {
	}

	public record Summary(int tasksTotal, int tasksDone, int habitsScheduled, int habitsDone) {
	}

	public record Dashboard(LocalDate date, Summary summary, List<Task> tasks, List<Habit> habits,
			List<Goal> activeGoals, JournalEntry journal, List<Payment> duePayments) {
	}

	// ---------- Finans ----------
	public record Asset(Long id, String symbol, String name, String type, String priceCurrency, String unit,
			BigDecimal currentPrice, boolean priceFromRate, boolean hasTrades, String priceSource, String sourceSymbol,
			String priceError) {
	}

	/** @param sources kaynak → "ok" ya da hata mesajı */
	public record PriceStatus(java.time.Instant finishedAt, int updated, int failed, int ratesFixed,
			java.util.Map<String, String> sources) {
	}

	public record HistoricalRate(String currency, LocalDate requestedDate, LocalDate rateDate, BigDecimal rateTry) {
	}

	public record Trade(Long id, Long assetId, String assetSymbol, String assetName, String unit, String priceCurrency,
			String side, LocalDate date, BigDecimal quantity, BigDecimal unitPrice, BigDecimal usdTryRate,
			boolean rateEstimated, BigDecimal fee, String note, BigDecimal totalTry, BigDecimal totalUsd) {
	}

	public record Rate(String currency, BigDecimal rateTry, java.time.Instant updatedAt) {
	}

	public record Holding(Long assetId, String symbol, String name, String type, String priceCurrency, String unit,
			BigDecimal quantity, BigDecimal avgCost, BigDecimal price, boolean priceIsFallback,
			String priceSource, java.time.Instant priceUpdatedAt, String priceError,
			BigDecimal costTry, BigDecimal costUsd, BigDecimal valueTry, BigDecimal valueUsd,
			BigDecimal unrealizedTry, BigDecimal unrealizedUsd, BigDecimal unrealizedPctTry, BigDecimal unrealizedPctUsd,
			BigDecimal realizedTry, BigDecimal realizedUsd, BigDecimal weight, boolean closed) {
	}

	public record Allocation(String type, BigDecimal valueTry, BigDecimal valueUsd, BigDecimal percent) {
	}

	public record Totals(BigDecimal valueTry, BigDecimal valueUsd, BigDecimal costTry, BigDecimal costUsd,
			BigDecimal unrealizedTry, BigDecimal unrealizedUsd, BigDecimal unrealizedPctTry, BigDecimal unrealizedPctUsd,
			BigDecimal realizedTry, BigDecimal realizedUsd) {
	}

	public record Portfolio(BigDecimal usdTry, boolean rateIsFallback, Totals totals, List<Allocation> allocation,
			List<Holding> holdings) {
	}

	public record Payment(Long id, String name, String category, BigDecimal amount, String currency, String frequency,
			LocalDate nextDueDate, long daysUntilDue, boolean active, String note, BigDecimal monthlyTry,
			LocalDate lastPaidDate) {
	}

	public record PaymentLog(Long id, Long paymentId, String paymentName, LocalDate dueDate, LocalDate paidDate,
			BigDecimal amount, String currency) {
	}

	public record PaymentsOverview(BigDecimal monthlyTotalTry, List<String> missingRates, BigDecimal paidThisMonthTry,
			List<Payment> payments, List<PaymentLog> recent) {
	}
}
