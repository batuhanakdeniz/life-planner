package com.example.demo.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.demo.project.ProjectDtos.Health;

class ProjectLogicTests {

	private static ProjectMetrics metrics(double uptime, double errorRate, int issues) {
		var days = List.of(new ProjectMetrics.Day(LocalDate.of(2026, 10, 15), 100, 3, null));
		return new ProjectMetrics(days, 1000, null, null, null, uptime, issues, errorRate, Instant.now());
	}

	@Test
	void slugifyHandlesTurkishCharacters() {
		assertThat(ProjectService.slugify("Kampüs Pazarı")).isEqualTo("kampus-pazari");
		assertThat(ProjectService.slugify("  Şehir & Işık  Çağrı ")).isEqualTo("sehir-isik-cagri");
		assertThatThrownBy(() -> ProjectService.slugify("!!!")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void healthThresholds() {
		var reasons = new ArrayList<String>();
		assertThat(ProjectService.health(metrics(99.97, 0.3, 3), reasons)).isEqualTo(Health.OK);
		assertThat(reasons).isEmpty();

		assertThat(ProjectService.health(metrics(99.2, 2.8, 11), reasons)).isEqualTo(Health.WARNING);
		assertThat(reasons).hasSize(3).anySatisfy(r -> assertThat(r).contains("Hata oranı").contains("%2,8"));

		reasons.clear();
		assertThat(ProjectService.health(metrics(97.5, 0.1, 0), reasons)).isEqualTo(Health.DOWN);
		assertThat(reasons).singleElement().asString().contains("%97,50");
	}

	@Test
	void mockIsStableWithinADayAndShiftsNextDay() {
		var p = new Project();
		p.setSlug("kampus-pazari");
		var source = new MockMetricsSource();
		var d = LocalDate.of(2026, 10, 15);
		var a = source.fetch(p, d).daily();
		var b = source.fetch(p, d).daily();
		assertThat(a).isEqualTo(b);
		assertThat(a).hasSize(30);
		// Ertesi gün: dünkü "bugün" değeri aynen bir önceki güne kayar
		var next = source.fetch(p, d.plusDays(1)).daily();
		assertThat(next.get(28).date()).isEqualTo(a.get(29).date());
		assertThat(next.get(28).activeUsers()).isNotEqualTo(0);
	}

	@Test
	void summaryComputesWeekOverWeekAndTotals() {
		var p = new Project();
		p.setSlug("x");
		p.setName("X");
		p.setMetricsSource("mock");
		var days = new ArrayList<ProjectMetrics.Day>();
		for (int i = 0; i < 30; i++) {
			days.add(new ProjectMetrics.Day(LocalDate.of(2026, 9, 16).plusDays(i), i == 22 ? 100 : i == 29 ? 110 : 50, 2, BigDecimal.TEN));
		}
		var m = new ProjectMetrics(days, 900, 1.0, BigDecimal.valueOf(300), 2.0, 99.9, 1, 0.2, Instant.now());
		var s = ProjectService.summarize(p, m);
		assertThat(s.activeUsers()).isEqualTo(110);
		assertThat(s.activeUsersChangePct()).isEqualTo(10.0);
		assertThat(s.signups7d()).isEqualTo(14);
		assertThat(s.sparkline()).hasSize(30);
		assertThat(s.mock()).isTrue();

		var t = ProjectService.totals(List.of(s, s));
		assertThat(t.activeUsers()).isEqualTo(220);
		assertThat(t.activeUsersChangePct()).isEqualTo(10.0);
		assertThat(t.mrr()).isEqualByComparingTo("600");
		assertThat(t.healthy()).isEqualTo(2);
	}

	@Test
	void tableRoundTripsAndDerivesSlugFromName() {
		var rows = List.of(new ProjectTable.Row("Kampüs Pazarı", "kampus-pazari", "İkinci el | pazar", "web", "https://x.io/d", true));
		var parsed = ProjectTable.parse(ProjectTable.render(rows));
		assertThat(parsed.warnings()).isEmpty();
		assertThat(parsed.rows()).isEqualTo(rows);

		var md = """
				| Ad | Kod | Açıklama | Platform | Dashboard | Aktif |
				|---|---|---|---|---|---|
				| Yeni Proje |  |  |  |  | hayır |
				""";
		assertThat(ProjectTable.parse(md).rows())
				.containsExactly(new ProjectTable.Row("Yeni Proje", "yeni-proje", null, null, null, false));
	}
}
