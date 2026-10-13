package com.example.demo.habit;

import java.time.LocalDate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Bir alışkanlığın belirli bir günde yapıldığını kaydeder. */
@Entity
@Table(name = "habit_logs", uniqueConstraints = @UniqueConstraint(columnNames = { "habit_id", "date" }))
@Getter
@Setter
@NoArgsConstructor
public class HabitLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Habit habit;

	@Column(nullable = false)
	private LocalDate date;

	public HabitLog(Habit habit, LocalDate date) {
		this.habit = habit;
		this.date = date;
	}
}
