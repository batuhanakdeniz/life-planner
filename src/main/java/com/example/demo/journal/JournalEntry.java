package com.example.demo.journal;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Her gün için tek bir günlük kaydı. */
@Entity
@Table(name = "journal_entries")
@Getter
@Setter
@NoArgsConstructor
public class JournalEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private LocalDate date;

	@Column(columnDefinition = "text")
	private String content;

	/** 1 (çok kötü) – 5 (harika). */
	private Integer mood;

	private Instant updatedAt = Instant.now();
}
