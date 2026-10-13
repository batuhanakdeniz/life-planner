package com.example.demo.journal;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.demo.journal.JournalDtos.JournalRequest;
import com.example.demo.journal.JournalDtos.JournalResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/journal")
@RequiredArgsConstructor
public class JournalController {

	private final JournalService service;

	/** GET /api/journal?from=..&to=.. (varsayılan: son 30 gün) */
	@GetMapping
	public List<JournalResponse> list(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		var end = to != null ? to : LocalDate.now();
		var start = from != null ? from : end.minusDays(30);
		return service.range(start, end);
	}

	@GetMapping("/{date}")
	public ResponseEntity<JournalResponse> get(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return ResponseEntity.of(service.find(date));
	}

	@PutMapping("/{date}")
	public JournalResponse save(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@Valid @RequestBody JournalRequest req) {
		return service.save(date, req);
	}

	@DeleteMapping("/{date}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		service.delete(date);
	}
}
