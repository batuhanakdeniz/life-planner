package com.example.demo.project;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ProjectDtos {

	private ProjectDtos() {
	}

	public enum Health { OK, WARNING, DOWN }

	/** Proje tanımı (ad, kod, açıklama, link). */
	public record ProjectRequest(
			@NotBlank @Size(max = 255) String name,
			@Size(max = 64) String slug,
			@Size(max = 500) String description,
			@Size(max = 64) String platform,
			@Size(max = 500) String dashboardUrl,
			Boolean active) {
	}

	public record ProjectInfo(Long id, String slug, String name, String description, String platform, String dashboardUrl,
			boolean active, String metricsSource) {
	}

	/**
	 * Kartta görünen özet.
	 *
	 * @param activeUsersChangePct bugünkü aktif kullanıcının 7 gün öncesine göre değişimi
	 * @param healthReasons        uyarı/kesinti sebepleri; sağlıklıysa boş
	 * @param sparkline            son 30 günün aktif kullanıcı sayıları
	 * @param mock                 veriler örnek (sahte) mi
	 */
	public record ProjectSummary(String slug, String name, String description, String platform, String dashboardUrl,
			Health health, List<String> healthReasons, long activeUsers, Double activeUsersChangePct, long mau,
			Double mauChangePct, long signups7d, BigDecimal mrr, Double mrrChangePct, double uptime30d, int openIssues,
			double errorRate24h, Instant lastDeployAt, List<Long> sparkline, boolean mock) {
	}

	public record Totals(long activeUsers, Double activeUsersChangePct, long mau, BigDecimal mrr, int projects, int healthy,
			int warning, int down) {
	}

	public record Overview(Instant generatedAt, Totals totals, List<ProjectSummary> projects) {
	}

	public record DailyPoint(LocalDate date, long activeUsers, long signups, BigDecimal revenue) {
	}

	public record ProjectDetail(ProjectSummary summary, List<DailyPoint> daily) {
	}
}
