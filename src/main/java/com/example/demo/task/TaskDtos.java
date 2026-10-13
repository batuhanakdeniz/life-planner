package com.example.demo.task;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class TaskDtos {

	private TaskDtos() {
	}

	public record TaskRequest(
			@NotBlank @Size(max = 255) String title,
			@Size(max = 2000) String description,
			LocalDate dueDate,
			Priority priority,
			Long goalId) {
	}

	public record StatusRequest(@NotNull TaskStatus status) {
	}

	public record TaskResponse(
			Long id,
			String title,
			String description,
			LocalDate dueDate,
			Priority priority,
			TaskStatus status,
			boolean completed,
			Instant completedAt,
			Long goalId,
			String goalTitle,
			boolean overdue) {

		static TaskResponse from(Task t, LocalDate today) {
			var goal = t.getGoal();
			boolean overdue = !t.isCompleted() && t.getDueDate() != null && t.getDueDate().isBefore(today);
			return new TaskResponse(t.getId(), t.getTitle(), t.getDescription(), t.getDueDate(), t.getPriority(),
					t.getStatus(), t.isCompleted(), t.getCompletedAt(), goal != null ? goal.getId() : null,
					goal != null ? goal.getTitle() : null, overdue);
		}
	}
}
