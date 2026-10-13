package com.example.demo.task;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TaskRepository extends JpaRepository<Task, Long> {

	@Query("""
			select t from Task t
			where t.dueDate = :date
			   or (t.dueDate < :date and t.completed = false)
			""")
	List<Task> findForDay(LocalDate date);

	List<Task> findByDueDateIsNullOrderByCreatedAtDesc();

	List<Task> findByDueDateBetween(LocalDate from, LocalDate to);

	List<Task> findByGoalId(Long goalId);

	/** Kanban: bitmemiş tüm görevler + yakın zamanda bitenler. */
	@Query("select t from Task t where t.completed = false or t.completedAt >= :doneSince")
	List<Task> findForBoard(Instant doneSince);
}
