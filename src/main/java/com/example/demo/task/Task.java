package com.example.demo.task;

import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.ColumnDefault;

import com.example.demo.goal.Goal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tasks", indexes = @Index(columnList = "dueDate"))
@Getter
@Setter
@NoArgsConstructor
public class Task {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String title;

	@Column(length = 2000)
	private String description;

	/** Görevin planlandığı gün. Boşsa "bir gün yapılacak" listesindedir. */
	private LocalDate dueDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Priority priority = Priority.MEDIUM;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@ColumnDefault("'TODO'")
	private TaskStatus status = TaskStatus.TODO;

	private boolean completed;

	private Instant completedAt;

	/** İsteğe bağlı: görev bir hedefe hizmet ediyorsa. */
	@ManyToOne(fetch = FetchType.LAZY)
	private Goal goal;

	@Column(nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	public void setCompleted(boolean completed) {
		setStatus(completed ? TaskStatus.DONE : TaskStatus.TODO);
	}

	public void setStatus(TaskStatus status) {
		boolean wasCompleted = this.completed;
		this.status = status;
		this.completed = status == TaskStatus.DONE;
		if (completed != wasCompleted) {
			this.completedAt = completed ? Instant.now() : null;
		}
	}
}
