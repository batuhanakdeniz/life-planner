package com.example.demo.habit;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.common.NotFoundException;
import com.example.demo.habit.HabitDtos.DayStatus;
import com.example.demo.habit.HabitDtos.HabitRequest;
import com.example.demo.habit.HabitDtos.HabitResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class HabitService {

	private final HabitRepository habits;
	private final HabitLogRepository logs;

	@Transactional(readOnly = true)
	public List<HabitResponse> list(LocalDate date, boolean includeArchived) {
		var result = includeArchived ? habits.findAllByOrderByArchivedAscCreatedAtAsc()
				: habits.findByArchivedFalseOrderByCreatedAt();
		return result.stream().map(h -> toResponse(h, date)).toList();
	}

	/** Belirtilen aralıkta alışkanlığın yapıldığı günler (ısı haritası için). */
	@Transactional(readOnly = true)
	public List<LocalDate> history(Long id, LocalDate from, LocalDate to) {
		get(id);
		return logs.findByHabitIdAndDateBetween(id, from, to).stream().map(HabitLog::getDate).sorted().toList();
	}

	public HabitResponse create(HabitRequest req) {
		var habit = new Habit();
		apply(habit, req);
		return toResponse(habits.save(habit), LocalDate.now());
	}

	public HabitResponse update(Long id, HabitRequest req) {
		var habit = get(id);
		apply(habit, req);
		return toResponse(habit, LocalDate.now());
	}

	public void delete(Long id) {
		var habit = get(id);
		logs.deleteByHabitId(id);
		habits.delete(habit);
	}

	public HabitResponse check(Long id, LocalDate date) {
		var habit = get(id);
		if (date.isAfter(LocalDate.now())) {
			throw new IllegalArgumentException("Gelecek bir gün işaretlenemez");
		}
		if (logs.findByHabitIdAndDate(id, date).isEmpty()) {
			logs.save(new HabitLog(habit, date));
		}
		return toResponse(habit, date);
	}

	public HabitResponse uncheck(Long id, LocalDate date) {
		var habit = get(id);
		logs.findByHabitIdAndDate(id, date).ifPresent(logs::delete);
		logs.flush();
		return toResponse(habit, date);
	}

	private Habit get(Long id) {
		return habits.findById(id).orElseThrow(() -> new NotFoundException("Alışkanlık", id));
	}

	private void apply(Habit habit, HabitRequest req) {
		habit.setName(req.name());
		habit.setDescription(req.description());
		habit.setIcon(req.icon());
		habit.setColor(req.color());
		habit.getDays().clear();
		if (req.days() != null) {
			habit.getDays().addAll(req.days());
		}
		if (req.archived() != null) {
			habit.setArchived(req.archived());
		}
	}

	private HabitResponse toResponse(Habit h, LocalDate date) {
		Set<LocalDate> doneDates = logs.findByHabitIdOrderByDateDesc(h.getId()).stream()
				.map(HabitLog::getDate)
				.collect(Collectors.toCollection(TreeSet::new));

		var last7 = date.minusDays(6).datesUntil(date.plusDays(1))
				.map(d -> new DayStatus(d, h.isScheduledOn(d), doneDates.contains(d)))
				.toList();

		var created = LocalDate.ofInstant(h.getCreatedAt(), ZoneId.systemDefault());
		var startDate = doneDates.stream().min(LocalDate::compareTo).filter(d -> d.isBefore(created)).orElse(created);

		return new HabitResponse(h.getId(), h.getName(), h.getDescription(), h.getIcon(), h.getColor(),
				h.getDays().isEmpty() ? EnumSet.noneOf(java.time.DayOfWeek.class) : EnumSet.copyOf(h.getDays()),
				h.isArchived(), startDate, h.isScheduledOn(date), doneDates.contains(date),
				currentStreak(h, doneDates, date.isAfter(LocalDate.now()) ? LocalDate.now() : date), longestStreak(h, doneDates), last7);
	}

	/**
	 * Verilen günden geriye doğru, planlı günlerde kesintisiz yapılma sayısı.
	 * Bugün henüz yapılmadıysa seri bozulmuş sayılmaz; dünden itibaren sayılır.
	 */
	static int currentStreak(Habit h, Set<LocalDate> doneDates, LocalDate date) {
		if (doneDates.isEmpty()) {
			return 0;
		}
		LocalDate first = doneDates.stream().min(LocalDate::compareTo).orElseThrow();
		LocalDate d = doneDates.contains(date) ? date : date.minusDays(1);
		int streak = 0;
		while (!d.isBefore(first)) {
			if (h.isScheduledOn(d)) {
				if (!doneDates.contains(d)) {
					break;
				}
				streak++;
			}
			d = d.minusDays(1);
		}
		return streak;
	}

	static int longestStreak(Habit h, Set<LocalDate> doneDates) {
		if (doneDates.isEmpty()) {
			return 0;
		}
		var sorted = new TreeSet<>(doneDates);
		int best = 0;
		int run = 0;
		for (LocalDate d = sorted.first(); !d.isAfter(sorted.last()); d = d.plusDays(1)) {
			if (!h.isScheduledOn(d)) {
				continue;
			}
			run = sorted.contains(d) ? run + 1 : 0;
			best = Math.max(best, run);
		}
		return best;
	}
}
