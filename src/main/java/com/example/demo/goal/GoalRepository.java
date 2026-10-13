package com.example.demo.goal;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<Goal, Long> {

	@EntityGraph(attributePaths = "milestones")
	List<Goal> findByStatusOrderByTargetDateAsc(GoalStatus status);

	@EntityGraph(attributePaths = "milestones")
	List<Goal> findAllByOrderByStatusAscTargetDateAsc();
}
