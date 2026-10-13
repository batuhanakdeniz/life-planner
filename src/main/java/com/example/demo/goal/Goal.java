package com.example.demo.goal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "goals")
@Getter
@Setter
@NoArgsConstructor
public class Goal {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String title;

	@Column(length = 4000)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private GoalCategory category = GoalCategory.PERSONAL;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private GoalStatus status = GoalStatus.ACTIVE;

	private LocalDate targetDate;

	@OneToMany(mappedBy = "goal", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position")
	private List<Milestone> milestones = new ArrayList<>();

	@Column(nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	/** Tamamlanan kilometre taşı oranı (0-100). Kilometre taşı yoksa durumdan türetilir. */
	public int progress() {
		if (milestones.isEmpty()) {
			return status == GoalStatus.COMPLETED ? 100 : 0;
		}
		long done = milestones.stream().filter(Milestone::isDone).count();
		return (int) Math.round(done * 100.0 / milestones.size());
	}

	public Milestone addMilestone(String title) {
		var m = new Milestone();
		m.setGoal(this);
		m.setTitle(title);
		m.setPosition(milestones.size());
		milestones.add(m);
		return m;
	}
}
