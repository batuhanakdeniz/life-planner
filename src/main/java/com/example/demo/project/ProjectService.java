package com.example.demo.project;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.common.NotFoundException;
import com.example.demo.project.ProjectDtos.*;

@Service
@Transactional
public class ProjectService {

	static final ZoneId TR = ZoneId.of("Europe/Istanbul");

	// Sağlık eşikleri
	static final double ERROR_RATE_WARN = 1.0;
	static final double UPTIME_WARN = 99.5;
	static final double UPTIME_DOWN = 98.0;
	static final int OPEN_ISSUES_WARN = 10;

	/** Projeler değişti (Obsidian dosyasının yeniden yazılması için). */
	public record ProjectsChanged() {
	}

	private final ProjectRepository projects;
	private final Map<String, ProjectMetricsSource> sources;
	private final ApplicationEventPublisher events;

	public ProjectService(ProjectRepository projects, List<ProjectMetricsSource> sources, ApplicationEventPublisher events) {
		this.projects = projects;
		this.sources = sources.stream().collect(Collectors.toMap(ProjectMetricsSource::name, Function.identity()));
		this.events = events;
	}

	// ---------- Özet ve detay ----------
	@Transactional(readOnly = true)
	public Overview overview() {
		var today = LocalDate.now(TR);
		var summaries = projects.findAllByOrderByPositionAscIdAsc().stream().filter(Project::isActive)
				.map(p -> summarize(p, metrics(p, today))).toList();
		return new Overview(Instant.now(), totals(summaries), summaries);
	}

	@Transactional(readOnly = true)
	public ProjectDetail detail(String slug) {
		var p = projects.findBySlugIgnoreCase(slug).orElseThrow(() -> new NotFoundException("Proje", slug));
		var m = metrics(p, LocalDate.now(TR));
		var daily = m.daily().stream().map(d -> new DailyPoint(d.date(), d.activeUsers(), d.signups(), d.revenue())).toList();
		return new ProjectDetail(summarize(p, m), daily);
	}

	private ProjectMetrics metrics(Project p, LocalDate today) {
		var source = sources.get(p.getMetricsSource());
		if (source == null) {
			throw new IllegalStateException("Bilinmeyen metrik kaynağı: " + p.getMetricsSource());
		}
		return source.fetch(p, today);
	}

	static ProjectSummary summarize(Project p, ProjectMetrics m) {
		var days = m.daily();
		var today = days.get(days.size() - 1);
		var weekAgo = days.size() > 7 ? days.get(days.size() - 8) : null;
		long signups7d = days.subList(Math.max(0, days.size() - 7), days.size()).stream().mapToLong(ProjectMetrics.Day::signups).sum();
		var reasons = new ArrayList<String>();
		var health = health(m, reasons);
		return new ProjectSummary(p.getSlug(), p.getName(), p.getDescription(), p.getPlatform(), p.getDashboardUrl(), health,
				reasons, today.activeUsers(), weekAgo != null ? changePct(today.activeUsers(), weekAgo.activeUsers()) : null,
				m.mau(), m.mauChangePct(), signups7d, m.mrr(), m.mrrChangePct(), m.uptime30d(), m.openIssues(),
				m.errorRate24h(), m.lastDeployAt(), days.stream().map(ProjectMetrics.Day::activeUsers).toList(),
				"mock".equals(p.getMetricsSource()));
	}

	/** Eşiklere göre sağlık durumu; sebepler Türkçe tek satırlık açıklamalar olarak eklenir. */
	static Health health(ProjectMetrics m, List<String> reasons) {
		if (m.uptime30d() < UPTIME_DOWN) {
			reasons.add(String.format(Locale.forLanguageTag("tr"), "Erişilebilirlik %%%.2f (eşik %%%.1f)", m.uptime30d(), UPTIME_DOWN));
			return Health.DOWN;
		}
		if (m.errorRate24h() > ERROR_RATE_WARN) {
			reasons.add(String.format(Locale.forLanguageTag("tr"), "Hata oranı son 24 saatte %%%.1f (eşik %%%.0f)", m.errorRate24h(), ERROR_RATE_WARN));
		}
		if (m.uptime30d() < UPTIME_WARN) {
			reasons.add(String.format(Locale.forLanguageTag("tr"), "30 günlük erişilebilirlik %%%.2f (eşik %%%.1f)", m.uptime30d(), UPTIME_WARN));
		}
		if (m.openIssues() >= OPEN_ISSUES_WARN) {
			reasons.add(m.openIssues() + " açık hata (eşik " + OPEN_ISSUES_WARN + ")");
		}
		return reasons.isEmpty() ? Health.OK : Health.WARNING;
	}

	static Totals totals(List<ProjectSummary> list) {
		long active = list.stream().mapToLong(ProjectSummary::activeUsers).sum();
		// Haftalık değişim: her projenin 7 gün önceki değeri, değişim oranından geri hesaplanır
		double weekAgo = list.stream().filter(s -> s.activeUsersChangePct() != null)
				.mapToDouble(s -> s.activeUsers() / (1 + s.activeUsersChangePct() / 100)).sum();
		var mrr = list.stream().map(ProjectSummary::mrr).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
		return new Totals(active, weekAgo > 0 ? changePct(active, weekAgo) : null,
				list.stream().mapToLong(ProjectSummary::mau).sum(), mrr, list.size(),
				(int) list.stream().filter(s -> s.health() == Health.OK).count(),
				(int) list.stream().filter(s -> s.health() == Health.WARNING).count(),
				(int) list.stream().filter(s -> s.health() == Health.DOWN).count());
	}

	static Double changePct(double now, double before) {
		if (before == 0) {
			return null;
		}
		return BigDecimal.valueOf((now - before) / before * 100).setScale(1, RoundingMode.HALF_UP).doubleValue();
	}

	// ---------- Proje tanımları ----------
	@Transactional(readOnly = true)
	public List<ProjectInfo> list() {
		return projects.findAllByOrderByPositionAscIdAsc().stream().map(ProjectService::info).toList();
	}

	public ProjectInfo create(ProjectRequest req) {
		var slug = slugify(req.slug() != null && !req.slug().isBlank() ? req.slug() : req.name());
		if (projects.findBySlugIgnoreCase(slug).isPresent()) {
			throw new IllegalArgumentException("Bu kodla bir proje zaten var: " + slug);
		}
		var p = new Project();
		p.setSlug(slug);
		p.setPosition((int) projects.count());
		apply(p, req);
		var saved = projects.save(p);
		events.publishEvent(new ProjectsChanged());
		return info(saved);
	}

	public ProjectInfo update(String slug, ProjectRequest req) {
		var p = projects.findBySlugIgnoreCase(slug).orElseThrow(() -> new NotFoundException("Proje", slug));
		apply(p, req);
		events.publishEvent(new ProjectsChanged());
		return info(p);
	}

	public void delete(String slug) {
		var p = projects.findBySlugIgnoreCase(slug).orElseThrow(() -> new NotFoundException("Proje", slug));
		projects.delete(p);
		events.publishEvent(new ProjectsChanged());
	}

	/** İlk açılışta, hiç proje yoksa örnek 3 projeyi ekler. */
	public boolean seedExamplesIfEmpty() {
		if (projects.count() > 0) {
			return false;
		}
		int pos = 0;
		for (var e : List.of(
				new String[] { "kampus-pazari", "Kampüs Pazarı", "Öğrenciler arası ikinci el pazarı", "web + mobil" },
				new String[] { "fittakip", "FitTakip", "Antrenman ve beslenme takibi", "mobil" },
				new String[] { "lezzet-rehberi", "Lezzet Rehberi", "Tarif ve restoran rehberi", "web" })) {
			var p = new Project();
			p.setSlug(e[0]);
			p.setName(e[1]);
			p.setDescription(e[2]);
			p.setPlatform(e[3]);
			p.setDashboardUrl("https://example.com/" + e[0] + "/dashboard");
			p.setPosition(pos++);
			projects.save(p);
		}
		return true;
	}

	private static void apply(Project p, ProjectRequest req) {
		p.setName(req.name().trim());
		p.setDescription(blankToNull(req.description()));
		p.setPlatform(blankToNull(req.platform()));
		p.setDashboardUrl(blankToNull(req.dashboardUrl()));
		if (req.active() != null) {
			p.setActive(req.active());
		}
	}

	static ProjectInfo info(Project p) {
		return new ProjectInfo(p.getId(), p.getSlug(), p.getName(), p.getDescription(), p.getPlatform(), p.getDashboardUrl(),
				p.isActive(), p.getMetricsSource());
	}

	/** "Kampüs Pazarı" → "kampus-pazari" */
	static String slugify(String s) {
		var t = s.trim().toLowerCase(Locale.forLanguageTag("tr")).replace('ı', 'i');
		t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		t = t.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
		if (t.isEmpty()) {
			throw new IllegalArgumentException("Proje kodu boş olamaz");
		}
		return t.length() > 64 ? t.substring(0, 64) : t;
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
