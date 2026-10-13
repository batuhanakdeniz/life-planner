package com.example.demo.habit;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "habits")
@Getter
@Setter
@NoArgsConstructor
public class Habit {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(length = 1000)
	private String description;

	/** Arayüzde gösterilecek emoji, ör. "📚". */
	private String icon;

	/** Hex renk, ör. "#4f46e5". */
	private String color;

	/** Alışkanlığın yapılacağı günler. Boş küme = her gün. */
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "habit_days")
	@Enumerated(EnumType.STRING)
	@Column(name = "day_of_week")
	private Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);

	private boolean archived;

	@Column(nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	public boolean isScheduledOn(LocalDate date) {
		return days.isEmpty() || days.contains(date.getDayOfWeek());
	}
}
