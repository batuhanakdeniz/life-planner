package com.example.demo.journal;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public final class JournalDtos {

	private JournalDtos() {
	}

	public record JournalRequest(@Size(max = 20000) String content, @Min(1) @Max(5) Integer mood) {
	}

	public record JournalResponse(Long id, LocalDate date, String content, Integer mood) {

		public static JournalResponse from(JournalEntry e) {
			return new JournalResponse(e.getId(), e.getDate(), e.getContent(), e.getMood());
		}
	}
}
