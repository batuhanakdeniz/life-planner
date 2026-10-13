package com.example.demo.task;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.example.demo.task.TaskDtos.StatusRequest;
import com.example.demo.task.TaskDtos.TaskRequest;
import com.example.demo.task.TaskDtos.TaskResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

	private final TaskService service;

	/**
	 * GET /api/tasks?date=2026-10-13 → o günün görevleri (+ gecikmiş olanlar)
	 * GET /api/tasks?from=..&to=..  → tarih aralığı (haftalık görünüm)
	 * GET /api/tasks                → tarihsiz "bir gün" görevleri
	 */
	@GetMapping
	public List<TaskResponse> list(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		if (date != null) {
			return service.forDay(date);
		}
		if (from != null && to != null) {
			return service.inRange(from, to);
		}
		return service.someday();
	}

	/** Kanban panosu: açık görevler + son 7 günde bitenler. */
	@GetMapping("/board")
	public List<TaskResponse> board() {
		return service.board();
	}

	@PatchMapping("/{id}/status")
	public TaskResponse setStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
		return service.setStatus(id, req.status());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TaskResponse create(@Valid @RequestBody TaskRequest req) {
		return service.create(req);
	}

	@PutMapping("/{id}")
	public TaskResponse update(@PathVariable Long id, @Valid @RequestBody TaskRequest req) {
		return service.update(id, req);
	}

	@PostMapping("/{id}/complete")
	public TaskResponse complete(@PathVariable Long id) {
		return service.setCompleted(id, true);
	}

	@DeleteMapping("/{id}/complete")
	public TaskResponse uncomplete(@PathVariable Long id) {
		return service.setCompleted(id, false);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		service.delete(id);
	}
}
