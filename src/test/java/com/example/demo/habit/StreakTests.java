package com.example.demo.habit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;

class StreakTests {

	private static final LocalDate MON = LocalDate.of(2026, 10, 12);

	@Test
	void dailyStreakCountsConsecutiveDays() {
		var habit = new Habit();
		var done = Set.of(MON, MON.minusDays(1), MON.minusDays(2), MON.minusDays(4));
		assertThat(HabitService.currentStreak(habit, done, MON)).isEqualTo(3);
		assertThat(HabitService.longestStreak(habit, done)).isEqualTo(3);
	}

	@Test
	void todayNotDoneYetDoesNotBreakStreak() {
		var habit = new Habit();
		var done = Set.of(MON.minusDays(1), MON.minusDays(2));
		assertThat(HabitService.currentStreak(habit, done, MON)).isEqualTo(2);
	}

	@Test
	void unscheduledDaysAreSkipped() {
		var habit = new Habit();
		habit.getDays().addAll(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));
		// Pzt 12, Cum 9, Çar 7 → arada Salı/Perşembe/hafta sonu yok sayılır
		var done = Set.of(MON, MON.minusDays(3), MON.minusDays(5));
		assertThat(HabitService.currentStreak(habit, done, MON)).isEqualTo(3);
	}
}
