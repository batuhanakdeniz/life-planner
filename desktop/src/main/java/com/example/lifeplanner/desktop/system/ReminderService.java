package com.example.lifeplanner.desktop.system;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.example.lifeplanner.desktop.api.ApiClient;
import com.example.lifeplanner.desktop.api.Models.Summary;

/** Ayarlanan saatlerde günün kalan işlerini bildirim olarak hatırlatır. */
public class ReminderService {

	private final ApiClient api;
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
		var t = new Thread(r, "reminders");
		t.setDaemon(true);
		return t;
	});
	private LocalDateTime lastFired;

	public ReminderService(ApiClient api) {
		this.api = api;
	}

	public void start() {
		scheduler.scheduleAtFixedRate(this::tick, 5, 30, TimeUnit.SECONDS);
	}

	private void tick() {
		if (!Settings.remindersEnabled()) {
			return;
		}
		var now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
		var time = now.toLocalTime();
		boolean due = Settings.reminderTimes().stream().anyMatch(t -> t.equals(time));
		if (due && !now.equals(lastFired)) {
			lastFired = now;
			sendNow();
		}
	}

	/** Bildirimi hemen gönderir (ayarlardaki "Test" düğmesi de bunu kullanır). */
	public void sendNow() {
		try {
			var d = api.dashboard(LocalDate.now());
			var dueToday = d.duePayments() == null ? 0
					: (int) d.duePayments().stream().filter(p -> p.daysUntilDue() <= 0).count();
			Notifier.notify("Hayat Planlayıcı", message(d.summary(), dueToday, LocalTime.now()));
		}
		catch (RuntimeException e) {
			Notifier.notify("Hayat Planlayıcı", "Hatırlatıcı: sunucuya bağlanılamadı.");
		}
	}

	/** @param paymentsDue vadesi bugün olan ya da geçmiş ödeme sayısı */
	static String message(Summary s, int paymentsDue, LocalTime time) {
		int tasksLeft = s.tasksTotal() - s.tasksDone();
		int habitsLeft = s.habitsScheduled() - s.habitsDone();
		if (tasksLeft == 0 && habitsLeft == 0 && paymentsDue == 0) {
			return "Bugün her şey tamam! 🎉";
		}
		var parts = new java.util.ArrayList<String>();
		if (paymentsDue > 0) {
			parts.add(paymentsDue + " ödeme");
		}
		if (tasksLeft > 0) {
			parts.add(tasksLeft + " görev");
		}
		if (habitsLeft > 0) {
			parts.add(habitsLeft + " alışkanlık");
		}
		String greeting = time.getHour() < 12 ? "Günaydın! Bugün " : "Bugün hâlâ ";
		return greeting + String.join(" ve ", parts) + " seni bekliyor.";
	}
}
