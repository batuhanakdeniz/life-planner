package com.example.demo.habit;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HabitLogRepository extends JpaRepository<HabitLog, Long> {

	Optional<HabitLog> findByHabitIdAndDate(Long habitId, LocalDate date);

	List<HabitLog> findByHabitIdAndDateBetween(Long habitId, LocalDate from, LocalDate to);

	List<HabitLog> findByHabitIdOrderByDateDesc(Long habitId);

	void deleteByHabitId(Long habitId);
}
