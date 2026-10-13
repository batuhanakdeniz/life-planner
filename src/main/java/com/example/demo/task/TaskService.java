package com.example.demo.task;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.common.NotFoundException;
import com.example.demo.goal.GoalRepository;
import com.example.demo.task.TaskDtos.TaskRequest;
import com.example.demo.task.TaskDtos.TaskResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class TaskService {

	/** Önce tamamlanmamışlar, sonra tarih, sonra yüksek öncelik. */
	private static final Comparator<Task> ORDER = Comparator.comparing(Task::isCompleted)
			.thenComparing(Task::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
			.thenComparing(Task::getPriority, Comparator.reverseOrder())
			.thenComparing(Task::getCreatedAt);

	private final TaskRepository tasks;
	private final GoalRepository goals;

	/** O günün görevleri + önceki günlerden kalan tamamlanmamış görevler. */
	@Transactional(readOnly = true)
	public List<TaskResponse> forDay(LocalDate date) {
		return tasks.findForDay(date).stream().sorted(ORDER).map(t -> TaskResponse.from(t, LocalDate.now())).toList();
	}

	@Transactional(readOnly = true)
	public List<TaskResponse> inRange(LocalDate from, LocalDate to) {
		return tasks.findByDueDateBetween(from, to).stream().sorted(ORDER)
				.map(t -> TaskResponse.from(t, LocalDate.now())).toList();
	}

	@Transactional(readOnly = true)
	public List<TaskResponse> someday() {
		return tasks.findByDueDateIsNullOrderByCreatedAtDesc().stream()
				.map(t -> TaskResponse.from(t, LocalDate.now())).toList();
	}

	@Transactional(readOnly = true)
	public List<TaskResponse> board() {
		return tasks.findForBoard(Instant.now().minus(7, ChronoUnit.DAYS)).stream().sorted(ORDER)
				.map(t -> TaskResponse.from(t, LocalDate.now())).toList();
	}

	public TaskResponse setStatus(Long id, TaskStatus status) {
		var task = get(id);
		task.setStatus(status);
		return TaskResponse.from(task, LocalDate.now());
	}

	public TaskResponse create(TaskRequest req) {
		var task = new Task();
		apply(task, req);
		return TaskResponse.from(tasks.save(task), LocalDate.now());
	}

	public TaskResponse update(Long id, TaskRequest req) {
		var task = get(id);
		apply(task, req);
		return TaskResponse.from(task, LocalDate.now());
	}

	public TaskResponse setCompleted(Long id, boolean completed) {
		var task = get(id);
		task.setCompleted(completed);
		return TaskResponse.from(task, LocalDate.now());
	}

	public void delete(Long id) {
		tasks.delete(get(id));
	}

	private Task get(Long id) {
		return tasks.findById(id).orElseThrow(() -> new NotFoundException("Görev", id));
	}

	private void apply(Task task, TaskRequest req) {
		task.setTitle(req.title());
		task.setDescription(req.description());
		task.setDueDate(req.dueDate());
		task.setPriority(req.priority() != null ? req.priority() : Priority.MEDIUM);
		task.setGoal(req.goalId() != null
				? goals.findById(req.goalId()).orElseThrow(() -> new NotFoundException("Hedef", req.goalId()))
				: null);
	}
}
