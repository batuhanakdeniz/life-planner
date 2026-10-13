package com.example.demo.dashboard;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.finance.PaymentDtos.PaymentResponse;
import com.example.demo.finance.PaymentService;
import com.example.demo.goal.GoalDtos.GoalResponse;
import com.example.demo.goal.GoalService;
import com.example.demo.goal.GoalStatus;
import com.example.demo.habit.HabitDtos.HabitResponse;
import com.example.demo.habit.HabitService;
import com.example.demo.journal.JournalDtos.JournalResponse;
import com.example.demo.journal.JournalService;
import com.example.demo.task.TaskDtos.TaskResponse;
import com.example.demo.task.TaskService;

import lombok.RequiredArgsConstructor;

/** "Bugün" ekranı için tüm verileri tek istekte döner. */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

	private final TaskService tasks;
	private final HabitService habits;
	private final GoalService goals;
	private final JournalService journal;
	private final PaymentService payments;

	public record Summary(int tasksTotal, int tasksDone, int habitsScheduled, int habitsDone) {
	}

	public record Dashboard(
			LocalDate date,
			Summary summary,
			List<TaskResponse> tasks,
			List<HabitResponse> habits,
			List<GoalResponse> activeGoals,
			JournalResponse journal,
			/** Vadesi geçmiş ya da birkaç gün içinde gelecek düzenli ödemeler. */
			List<PaymentResponse> duePayments) {
	}

	@GetMapping
	public Dashboard get(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		var day = date != null ? date : LocalDate.now();
		var dayTasks = tasks.forDay(day);
		var dayHabits = habits.list(day, false);
		var summary = new Summary(
				dayTasks.size(),
				(int) dayTasks.stream().filter(TaskResponse::completed).count(),
				(int) dayHabits.stream().filter(HabitResponse::scheduled).count(),
				(int) dayHabits.stream().filter(h -> h.scheduled() && h.doneOnDate()).count());
		return new Dashboard(day, summary, dayTasks, dayHabits, goals.list(GoalStatus.ACTIVE),
				journal.find(day).orElse(null), payments.dueSoon(day));
	}
}
