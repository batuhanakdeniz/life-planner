package com.example.demo.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

public enum Frequency {
	WEEKLY, MONTHLY, YEARLY;

	public LocalDate next(LocalDate from) {
		return switch (this) {
			case WEEKLY -> from.plusWeeks(1);
			case MONTHLY -> from.plusMonths(1);
			case YEARLY -> from.plusYears(1);
		};
	}

	public LocalDate previous(LocalDate from) {
		return switch (this) {
			case WEEKLY -> from.minusWeeks(1);
			case MONTHLY -> from.minusMonths(1);
			case YEARLY -> from.minusYears(1);
		};
	}

	/** Aylık eşdeğere çevirme çarpanı. */
	public BigDecimal perMonth() {
		return switch (this) {
			case WEEKLY -> new BigDecimal("4.333333");
			case MONTHLY -> BigDecimal.ONE;
			case YEARLY -> new BigDecimal("0.083333");
		};
	}
}
