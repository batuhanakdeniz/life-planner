package com.example.demo.habit;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.example.demo.habit.HabitDtos.HabitRequest;
import com.example.demo.habit.HabitDtos.HabitResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/habits")
@RequiredArgsConstructor
public class HabitController {

	private final HabitService service;

	@GetMapping
	public List<HabitResponse> list(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(defaultValue = "false") boolean includeArchived) {
		return service.list(date != null ? date : LocalDate.now(), includeArchived);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public HabitResponse create(@Valid @RequestBody HabitRequest req) {
		return service.create(req);
	}

	@PutMapping("/{id}")
	public HabitResponse update(@PathVariable Long id, @Valid @RequestBody HabitRequest req) {
		return service.update(id, req);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		service.delete(id);
	}

	/** GET /api/habits/3/history?from=..&to=.. → yapıldığı günlerin listesi (varsayılan: son 1 yıl) */
	@GetMapping("/{id}/history")
	public List<LocalDate> history(@PathVariable Long id,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		var end = to != null ? to : LocalDate.now();
		return service.history(id, from != null ? from : end.minusYears(1), end);
	}

	/** POST /api/habits/3/check?date=2026-10-13 → o gün yapıldı olarak işaretle (tarih boşsa bugün). */
	@PostMapping("/{id}/check")
	public HabitResponse check(@PathVariable Long id,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return service.check(id, date != null ? date : LocalDate.now());
	}

	@DeleteMapping("/{id}/check")
	public HabitResponse uncheck(@PathVariable Long id,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return service.uncheck(id, date != null ? date : LocalDate.now());
	}
}
