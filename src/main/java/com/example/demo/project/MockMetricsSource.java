package com.example.demo.project;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;

import org.springframework.stereotype.Component;

/**
 * Gerçekçi görünen sahte metrikler üretir. Değerler proje kodu ve gün ile belirlenir (deterministik):
 * aynı gün içinde sayfa yenilendiğinde değişmez, ertesi gün grafik bir gün kayar.
 */
@Component
public class MockMetricsSource implements ProjectMetricsSource {

	/**
	 * Bir projenin sahte veri profili.
	 *
	 * @param dau          bugünkü aktif kullanıcı (yaklaşık)
	 * @param weeklyGrowth haftalık büyüme oranı (0,11 = %11)
	 * @param mrr          aylık gelir; null ise gelir yok
	 */
	record Profile(long dau, double weeklyGrowth, long mau, double mauChangePct, Long mrr, double mrrChangePct,
			double uptime, int openIssues, double errorRate, int deployHoursAgo, double signupRate) {
	}

	/** Örnek projeler için elle ayarlanmış profiller. */
	static final Map<String, Profile> PROFILES = Map.of(
			"kampus-pazari", new Profile(2940, 0.11, 21_400, 5.4, 28_900L, 12.0, 99.97, 3, 0.3, 2, 0.03),
			"fittakip", new Profile(1380, -0.04, 12_150, -2.1, 14_200L, 3.5, 99.20, 11, 2.8, 96, 0.02),
			"lezzet-rehberi", new Profile(492, 0.23, 4_690, 18.7, 3_200L, 26.0, 100.0, 0, 0.1, 26, 0.04));

	@Override
	public String name() {
		return "mock";
	}

	@Override
	public ProjectMetrics fetch(Project project, LocalDate today) {
		var p = PROFILES.getOrDefault(project.getSlug(), generated(project.getSlug()));
		double dailyGrowth = Math.log(1 + p.weeklyGrowth()) / 7;
		long seed = project.getSlug().hashCode();

		var days = new ArrayList<ProjectMetrics.Day>();
		for (int back = 29; back >= 0; back--) {
			var date = today.minusDays(back);
			var rnd = new Random(seed * 31 + date.toEpochDay());
			// Hafta sonu hafif düşüş + gün başına ±%3 gürültü
			double weekday = switch (date.getDayOfWeek()) {
				case SATURDAY -> 0.93;
				case SUNDAY -> 0.90;
				default -> 1.0;
			};
			double noise = 1 + (rnd.nextDouble() - 0.5) * 0.06;
			long dau = Math.round(p.dau() * Math.exp(-dailyGrowth * back) * weekday * noise);
			long signups = Math.round(dau * p.signupRate() * (0.7 + rnd.nextDouble() * 0.6));
			BigDecimal revenue = p.mrr() == null ? null
					: BigDecimal.valueOf(p.mrr() / 30.0 * Math.exp(-dailyGrowth * back) * (0.6 + rnd.nextDouble() * 0.8))
							.setScale(2, RoundingMode.HALF_UP);
			days.add(new ProjectMetrics.Day(date, dau, signups, revenue));
		}
		return new ProjectMetrics(days, p.mau(), p.mauChangePct(), p.mrr() == null ? null : BigDecimal.valueOf(p.mrr()),
				p.mrr() == null ? null : p.mrrChangePct(), p.uptime(), p.openIssues(), p.errorRate(),
				Instant.now().minus(Duration.ofHours(p.deployHoursAgo())));
	}

	/** Profili olmayan (sonradan eklenen) projeler için koddan türetilmiş makul değerler. */
	static Profile generated(String slug) {
		var r = new Random(slug.hashCode());
		long dau = 100 + r.nextInt(3000);
		boolean paid = r.nextBoolean();
		return new Profile(dau, -0.05 + r.nextDouble() * 0.25, dau * (5 + r.nextInt(6)), -5 + r.nextDouble() * 20,
				paid ? (long) (dau * (2 + r.nextInt(10))) : null, -5 + r.nextDouble() * 25,
				99 + r.nextDouble(), r.nextInt(8), r.nextDouble() * 1.5, 1 + r.nextInt(120), 0.01 + r.nextDouble() * 0.04);
	}
}
