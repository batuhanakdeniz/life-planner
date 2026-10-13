package com.example.demo.finance.market;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import com.example.demo.finance.PortfolioService;

/**
 * Otomatik fiyat güncellemesi (Europe/Istanbul saatiyle):
 * hafta içi 09:00–23:45 arası 15 dakikada bir, diğer zamanlarda 3 saatte bir.
 * Testlerde {@code lifeplanner.prices.enabled=false} ile kapatılır.
 */
@Configuration
@ConditionalOnProperty(name = "lifeplanner.prices.enabled", havingValue = "true", matchIfMissing = true)
public class PriceScheduler {

	private final PriceUpdateService prices;
	private final PortfolioService portfolio;

	public PriceScheduler(PriceUpdateService prices, PortfolioService portfolio) {
		this.prices = prices;
		this.portfolio = portfolio;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onStartup() {
		portfolio.assignMissingSources();
		Thread.ofVirtual().name("price-startup").start(prices::refreshAll);
	}

	@Scheduled(cron = "0 */15 9-23 * * MON-FRI", zone = "Europe/Istanbul")
	public void marketHours() {
		prices.refreshAll();
	}

	@Scheduled(cron = "0 0 */3 * * *", zone = "Europe/Istanbul")
	public void offHours() {
		prices.refreshAll();
	}
}
