package com.example.demo.habit;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class HabitDtos {

	private HabitDtos() {
	}

	public record HabitRequest(
			@NotBlank @Size(max = 255) String name,
			@Size(max = 1000) String description,
			@Size(max = 16) String icon,
			@Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "#RRGGBB formatında olmalı") String color,
			Set<DayOfWeek> days,
			Boolean archived) {
	}

	/**
	 * @param doneOnDate  istenen günde yapıldı mı
	 * @param scheduled   istenen gün bu alışkanlığın günü mü
	 * @param currentStreak ardışık (planlı) günlerdeki seri
	 * @param last7Days   son 7 günün durumu, en eskiden yeniye
	 */
	public record HabitResponse(
			Long id,
			String name,
			String description,
			String icon,
			String color,
			Set<DayOfWeek> days,
			boolean archived,
			/** Oluşturulma günü ya da (daha eskiyse) ilk işaretlenen gün. */
			LocalDate startDate,
			boolean scheduled,
			boolean doneOnDate,
			int currentStreak,
			int longestStreak,
			List<DayStatus> last7Days) {
	}

	public record DayStatus(LocalDate date, boolean scheduled, boolean done) {
	}
}
