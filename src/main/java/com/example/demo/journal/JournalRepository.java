package com.example.demo.journal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalRepository extends JpaRepository<JournalEntry, Long> {

	Optional<JournalEntry> findByDate(LocalDate date);

	List<JournalEntry> findByDateBetweenOrderByDateDesc(LocalDate from, LocalDate to);
}
