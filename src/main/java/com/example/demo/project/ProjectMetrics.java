package com.example.demo.project;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Bir projenin ham metrikleri; nereden geldiğinden bağımsız. Bir {@link ProjectMetricsSource} doldurur.
 *
 * @param daily        son 30 günün günlük değerleri, eskiden yeniye
 * @param mau          aylık aktif kullanıcı
 * @param mauChangePct MAU'nun önceki 30 güne göre değişimi (%)
 * @param mrr          aylık tekrarlayan gelir; proje gelir elde etmiyorsa null
 * @param mrrChangePct MRR'ın önceki aya göre değişimi (%)
 * @param uptime30d    son 30 günde erişilebilirlik (%)
 * @param openIssues   açık hata/issue sayısı
 * @param errorRate24h son 24 saatte hatalı istek oranı (%)
 * @param lastDeployAt son yayına alma zamanı
 */
public record ProjectMetrics(List<Day> daily, long mau, Double mauChangePct, BigDecimal mrr, Double mrrChangePct,
		double uptime30d, int openIssues, double errorRate24h, Instant lastDeployAt) {

	/** Bir günün değerleri. revenue, günlük gelir (TL); gelir yoksa null. */
	public record Day(LocalDate date, long activeUsers, long signups, BigDecimal revenue) {
	}
}
