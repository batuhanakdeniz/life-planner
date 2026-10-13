package com.example.demo.goal;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "milestones")
@Getter
@Setter
@NoArgsConstructor
public class Milestone {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Goal goal;

	@Column(nullable = false)
	private String title;

	private int position;

	private boolean done;

	private Instant doneAt;

	public void setDone(boolean done) {
		this.done = done;
		this.doneAt = done ? Instant.now() : null;
	}
}
