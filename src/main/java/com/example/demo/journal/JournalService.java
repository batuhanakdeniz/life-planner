package com.example.demo.journal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.journal.JournalDtos.JournalRequest;
import com.example.demo.journal.JournalDtos.JournalResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class JournalService {

	private final JournalRepository journal;

	@Transactional(readOnly = true)
	public Optional<JournalResponse> find(LocalDate date) {
		return journal.findByDate(date).map(JournalResponse::from);
	}

	@Transactional(readOnly = true)
	public List<JournalResponse> range(LocalDate from, LocalDate to) {
		return journal.findByDateBetweenOrderByDateDesc(from, to).stream().map(JournalResponse::from).toList();
	}

	/** Günün kaydını oluşturur ya da günceller. */
	public JournalResponse save(LocalDate date, JournalRequest req) {
		var entry = journal.findByDate(date).orElseGet(() -> {
			var e = new JournalEntry();
			e.setDate(date);
			return e;
		});
		entry.setContent(req.content());
		entry.setMood(req.mood());
		entry.setUpdatedAt(Instant.now());
		return JournalResponse.from(journal.save(entry));
	}

	public void delete(LocalDate date) {
		journal.findByDate(date).ifPresent(journal::delete);
	}
}
