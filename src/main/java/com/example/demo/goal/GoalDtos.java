package com.example.demo.goal;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class GoalDtos {

	private GoalDtos() {
	}

	public record GoalRequest(
			@NotBlank @Size(max = 255) String title,
			@Size(max = 4000) String description,
			GoalCategory category,
			GoalStatus status,
			LocalDate targetDate,
			/** Yalnızca oluştururken kullanılır: ilk kilometre taşları. */
			List<@NotBlank String> milestones) {
	}

	public record MilestoneRequest(@NotBlank @Size(max = 255) String title) {
	}

	public record MilestoneResponse(Long id, String title, boolean done) {

		static MilestoneResponse from(Milestone m) {
			return new MilestoneResponse(m.getId(), m.getTitle(), m.isDone());
		}
	}

	public record GoalResponse(
			Long id,
			String title,
			String description,
			GoalCategory category,
			GoalStatus status,
			LocalDate targetDate,
			LocalDate startDate,
			int progress,
			List<MilestoneResponse> milestones) {

		public static GoalResponse from(Goal g) {
			return new GoalResponse(g.getId(), g.getTitle(), g.getDescription(), g.getCategory(), g.getStatus(),
					g.getTargetDate(), LocalDate.ofInstant(g.getCreatedAt(), ZoneId.systemDefault()), g.progress(), g.getMilestones().stream().map(MilestoneResponse::from).toList());
		}
	}
}
