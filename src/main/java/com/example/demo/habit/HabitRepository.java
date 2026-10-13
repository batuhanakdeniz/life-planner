package com.example.demo.habit;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HabitRepository extends JpaRepository<Habit, Long> {

	List<Habit> findByArchivedFalseOrderByCreatedAt();

	List<Habit> findAllByOrderByArchivedAscCreatedAtAsc();
}
