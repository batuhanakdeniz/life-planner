package com.example.demo.project;

import java.time.LocalDate;

/**
 * Proje metriklerinin kaynağı. Şimdilik yalnızca sahte veri üreten {@link MockMetricsSource} var.
 * Gerçek veriye geçerken (ör. analitik aracı, ödeme sağlayıcısı, uptime servisi) bu arayüzün yeni bir
 * uygulaması yazılır ve projenin {@code metricsSource} alanı onu gösterir.
 */
public interface ProjectMetricsSource {

	/** Bu kaynağın adı; Project.metricsSource ile eşleşir. */
	String name();

	ProjectMetrics fetch(Project project, LocalDate today);
}
