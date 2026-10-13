package com.example.lifeplanner.desktop.api;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.lifeplanner.desktop.api.Models.Dashboard;
import com.example.lifeplanner.desktop.api.Models.Goal;
import com.example.lifeplanner.desktop.api.Models.Habit;
import com.example.lifeplanner.desktop.api.Models.JournalEntry;
import com.example.lifeplanner.desktop.api.Models.Task;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** Backend API istemcisi. Tüm metotlar bloklayıcıdır; arayüz iş parçacığından çağrılmamalıdır. */
public class ApiClient {

	private static final TypeReference<List<Task>> TASKS = new TypeReference<>() { };
	private static final TypeReference<List<Habit>> HABITS = new TypeReference<>() { };
	private static final TypeReference<List<Goal>> GOALS = new TypeReference<>() { };
	private static final TypeReference<List<JournalEntry>> ENTRIES = new TypeReference<>() { };
	private static final TypeReference<List<LocalDate>> DATES = new TypeReference<>() { };

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
	private final ObjectMapper json = new ObjectMapper()
			.registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
	private final String base;

	public ApiClient(String base) {
		this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
	}

	/** null değerlere izin veren JSON gövdesi: body("title", "X", "dueDate", null) */
	public static Map<String, Object> body(Object... kv) {
		var map = new LinkedHashMap<String, Object>();
		for (int i = 0; i < kv.length; i += 2) {
			map.put((String) kv[i], kv[i + 1]);
		}
		return map;
	}

	// --- Dashboard ---
	public Dashboard dashboard(LocalDate date) {
		return send("GET", "/dashboard?date=" + date, null, new TypeReference<Dashboard>() { });
	}

	// --- Görevler ---
	public List<Task> tasksForDay(LocalDate date) { return send("GET", "/tasks?date=" + date, null, TASKS); }
	public List<Task> tasksInRange(LocalDate from, LocalDate to) { return send("GET", "/tasks?from=" + from + "&to=" + to, null, TASKS); }
	public List<Task> somedayTasks() { return send("GET", "/tasks", null, TASKS); }
	public List<Task> taskBoard() { return send("GET", "/tasks/board", null, TASKS); }
	public void createTask(Map<String, Object> task) { send("POST", "/tasks", task, null); }
	public void setTaskDone(long id, boolean done) { send(done ? "POST" : "DELETE", "/tasks/" + id + "/complete", null, null); }
	public void setTaskStatus(long id, String status) { send("PATCH", "/tasks/" + id + "/status", body("status", status), null); }
	public void deleteTask(long id) { send("DELETE", "/tasks/" + id, null, null); }

	// --- Alışkanlıklar ---
	public List<Habit> habits(LocalDate date, boolean includeArchived) {
		return send("GET", "/habits?date=" + date + "&includeArchived=" + includeArchived, null, HABITS);
	}
	public void createHabit(Map<String, Object> habit) { send("POST", "/habits", habit, null); }
	public void updateHabit(long id, Map<String, Object> habit) { send("PUT", "/habits/" + id, habit, null); }
	public void deleteHabit(long id) { send("DELETE", "/habits/" + id, null, null); }
	public void setHabitDone(long id, LocalDate date, boolean done) { send(done ? "POST" : "DELETE", "/habits/" + id + "/check?date=" + date, null, null); }
	public List<LocalDate> habitHistory(long id, LocalDate from, LocalDate to) {
		return send("GET", "/habits/" + id + "/history?from=" + from + "&to=" + to, null, DATES);
	}

	// --- Hedefler ---
	public List<Goal> goals(String status) { return send("GET", "/goals" + (status != null ? "?status=" + status : ""), null, GOALS); }
	public void createGoal(Map<String, Object> goal) { send("POST", "/goals", goal, null); }
	public void updateGoal(long id, Map<String, Object> goal) { send("PUT", "/goals/" + id, goal, null); }
	public void deleteGoal(long id) { send("DELETE", "/goals/" + id, null, null); }
	public void addMilestone(long goalId, String title) { send("POST", "/goals/" + goalId + "/milestones", body("title", title), null); }
	public void setMilestoneDone(long id, boolean done) { send(done ? "POST" : "DELETE", "/goals/milestones/" + id + "/done", null, null); }
	public void deleteMilestone(long id) { send("DELETE", "/goals/milestones/" + id, null, null); }

	// --- Finans ---
	public Models.Portfolio portfolio() { return send("GET", "/finance/portfolio", null, new TypeReference<Models.Portfolio>() { }); }
	public List<Models.Asset> assets() { return send("GET", "/finance/assets", null, new TypeReference<List<Models.Asset>>() { }); }
	public void createAsset(Map<String, Object> asset) { send("POST", "/finance/assets", asset, null); }
	public void setAssetPrice(long id, java.math.BigDecimal price) { send("PUT", "/finance/assets/" + id + "/price", body("price", price), null); }
	public void deleteAsset(long id) { send("DELETE", "/finance/assets/" + id, null, null); }
	public List<Models.Trade> trades(Long assetId) {
		return send("GET", "/finance/trades" + (assetId != null ? "?assetId=" + assetId : ""), null, new TypeReference<List<Models.Trade>>() { });
	}
	public void createTrade(Map<String, Object> trade) { send("POST", "/finance/trades", trade, null); }
	public void updateTrade(long id, Map<String, Object> trade) { send("PUT", "/finance/trades/" + id, trade, null); }
	public void deleteTrade(long id) { send("DELETE", "/finance/trades/" + id, null, null); }
	public List<Models.Rate> rates() { return send("GET", "/finance/rates", null, new TypeReference<List<Models.Rate>>() { }); }
	public void setRate(String currency, java.math.BigDecimal rateTry) { send("PUT", "/finance/rates/" + currency, body("rateTry", rateTry), null); }
	public Models.PaymentsOverview payments() { return send("GET", "/finance/payments", null, new TypeReference<Models.PaymentsOverview>() { }); }
	public void createPayment(Map<String, Object> p) { send("POST", "/finance/payments", p, null); }
	public void updatePayment(long id, Map<String, Object> p) { send("PUT", "/finance/payments/" + id, p, null); }
	public void deletePayment(long id) { send("DELETE", "/finance/payments/" + id, null, null); }
	public void payPayment(long id) { send("POST", "/finance/payments/" + id + "/pay", body(), null); }
	public void undoPayment(long logId) { send("DELETE", "/finance/payments/logs/" + logId, null, null); }

	// --- Günlük ---
	public List<JournalEntry> journal(LocalDate from, LocalDate to) { return send("GET", "/journal?from=" + from + "&to=" + to, null, ENTRIES); }
	public void saveJournal(LocalDate date, String content, Integer mood) { send("PUT", "/journal/" + date, body("content", content, "mood", mood), null); }

	private <T> T send(String method, String path, Object body, TypeReference<T> type) {
		try {
			var req = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10));
			if (body != null) {
				req.header("Content-Type", "application/json")
						.method(method, BodyPublishers.ofString(json.writeValueAsString(body)));
			}
			else {
				req.method(method, BodyPublishers.noBody());
			}
			var res = http.send(req.build(), BodyHandlers.ofString());
			if (res.statusCode() == 204) {
				return null;
			}
			if (res.statusCode() >= 400) {
				String detail;
				try {
					detail = json.readTree(res.body()).path("detail").asText("İstek başarısız: " + res.statusCode());
				}
				catch (IOException e) {
					detail = "İstek başarısız: " + res.statusCode();
				}
				throw new ApiException(detail);
			}
			return type == null ? null : json.readValue(res.body(), type);
		}
		catch (ConnectException e) {
			throw new ApiException("Sunucuya bağlanılamadı (" + base + "). Backend çalışıyor mu?");
		}
		catch (IOException e) {
			throw new ApiException("Bağlantı hatası: " + e.getMessage());
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ApiException("İstek iptal edildi");
		}
	}
}
