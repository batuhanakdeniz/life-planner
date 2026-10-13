package com.example.demo.goal;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.example.demo.goal.GoalDtos.GoalRequest;
import com.example.demo.goal.GoalDtos.GoalResponse;
import com.example.demo.goal.GoalDtos.MilestoneRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class GoalController {

	private final GoalService service;

	@GetMapping
	public List<GoalResponse> list(@RequestParam(required = false) GoalStatus status) {
		return service.list(status);
	}

	@GetMapping("/{id}")
	public GoalResponse get(@PathVariable Long id) {
		return service.find(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public GoalResponse create(@Valid @RequestBody GoalRequest req) {
		return service.create(req);
	}

	@PutMapping("/{id}")
	public GoalResponse update(@PathVariable Long id, @Valid @RequestBody GoalRequest req) {
		return service.update(id, req);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		service.delete(id);
	}

	@PostMapping("/{id}/milestones")
	public GoalResponse addMilestone(@PathVariable Long id, @Valid @RequestBody MilestoneRequest req) {
		return service.addMilestone(id, req.title());
	}

	@PostMapping("/milestones/{milestoneId}/done")
	public GoalResponse markDone(@PathVariable Long milestoneId) {
		return service.setMilestoneDone(milestoneId, true);
	}

	@DeleteMapping("/milestones/{milestoneId}/done")
	public GoalResponse markUndone(@PathVariable Long milestoneId) {
		return service.setMilestoneDone(milestoneId, false);
	}

	@DeleteMapping("/milestones/{milestoneId}")
	public GoalResponse deleteMilestone(@PathVariable Long milestoneId) {
		return service.deleteMilestone(milestoneId);
	}
}
