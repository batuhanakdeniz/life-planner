package com.example.demo.bulletin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class BulletinLogicTests {

	private static final LocalDate OCT14 = LocalDate.of(2026, 10, 14);

	private static ZonedDateTime tr(String dateTime) {
		return ZonedDateTime.of(java.time.LocalDateTime.parse(dateTime), BulletinService.TR);
	}

	// ---------- Zaman penceresi ----------
	@Test
	void defaultWindowIsPreviousNoonToNoonInTurkey() {
		var c = new BulletinChannel();
		c.setHandle("@kanal");
		var w = BulletinService.window(c, OCT14);
		assertThat(w.windowEnd()).isEqualTo(tr("2026-10-14T12:00").toInstant());
		assertThat(w.windowStart()).isEqualTo(tr("2026-10-13T12:00").toInstant());
		assertThat(w.url()).isEqualTo("https://www.youtube.com/@kanal/videos");
	}

	@Test
	void cutoffAfterRunTimeUsesPreviousDaysCutoff() {
		var c = new BulletinChannel();
		c.setHandle("@kanal");
		c.setCutoffTime(LocalTime.of(18, 0)); // 12:05'te henüz gelmemiş
		c.setLookbackHours(48);
		var w = BulletinService.window(c, OCT14);
		assertThat(w.windowEnd()).isEqualTo(tr("2026-10-13T18:00").toInstant());
		assertThat(w.windowStart()).isEqualTo(tr("2026-10-11T18:00").toInstant());
	}

	// ---------- Bekleyen günler (telafi) ----------
	@Test
	void beforeRunTimeTodayIsNotDueYet() {
		var last = Optional.of(OCT14.minusDays(1));
		assertThat(BulletinService.pendingDates(tr("2026-10-14T11:00"), last)).isEmpty();
		assertThat(BulletinService.pendingDates(tr("2026-10-14T12:10"), last)).containsExactly(OCT14);
	}

	@Test
	void missedDaysAreCaughtUpButCappedAtSeven() {
		assertThat(BulletinService.pendingDates(tr("2026-10-14T13:00"), Optional.of(LocalDate.of(2026, 10, 11))))
				.containsExactly(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 13), OCT14);
		var many = BulletinService.pendingDates(tr("2026-10-14T13:00"), Optional.of(LocalDate.of(2026, 9, 1)));
		assertThat(many).hasSize(7).first().isEqualTo(LocalDate.of(2026, 10, 8));
	}

	@Test
	void firstEverRunOnlyMakesLatestDay() {
		assertThat(BulletinService.pendingDates(tr("2026-10-14T09:00"), Optional.empty())).containsExactly(OCT14.minusDays(1));
	}

	// ---------- Kanallar.md ----------
	@Test
	void tableRoundTripsAndToleratesUserEdits() {
		var rows = List.of(new ChannelTable.Row("Kanal A", "@kanala", true, LocalTime.NOON, 24),
				new ChannelTable.Row("B | ekonomi", "https://www.youtube.com/@b", false, LocalTime.of(9, 30), 48));
		var parsed = ChannelTable.parse(ChannelTable.render(rows));
		assertThat(parsed.warnings()).isEmpty();
		assertThat(parsed.rows()).isEqualTo(rows);

		// Kullanıcının elle yazdığı satırlar: @ eksik, boş alanlar, Türkçe "hayır", tek haneli saat
		var md = """
				# Bülten kanalları
				| Ad | Kanal | Aktif | Kesim | Geriye (saat) |
				|---|---|---|---|---|
				| Ekonomi | ekonomikanal | hayır | 9:00 | 12 |
				| | @sadece | | | |
				| Bozuk | @bozuk | belki | 12:00 | 24 |
				""";
		var p = ChannelTable.parse(md);
		assertThat(p.rows()).containsExactly(
				new ChannelTable.Row("Ekonomi", "@ekonomikanal", false, LocalTime.of(9, 0), 12),
				new ChannelTable.Row("@sadece", "@sadece", true, LocalTime.NOON, 24));
		assertThat(p.warnings()).singleElement().asString().contains("@bozuk");
	}

	@Test
	void hashIgnoresRowOrder() {
		var a = new ChannelTable.Row("A", "@a", true, LocalTime.NOON, 24);
		var b = new ChannelTable.Row("B", "@b", true, LocalTime.NOON, 24);
		assertThat(ChannelTable.hash(List.of(a, b))).isEqualTo(ChannelTable.hash(List.of(b, a)));
		assertThat(ChannelTable.hash(List.of(a))).isNotEqualTo(ChannelTable.hash(List.of(a, b)));
	}

	@Test
	void handleNormalization() {
		assertThat(BulletinService.normalizeHandle(" TEDx ")).isEqualTo("@TEDx");
		assertThat(BulletinService.normalizeHandle("https://www.youtube.com/@TEDx/videos/")).isEqualTo("https://www.youtube.com/@TEDx");
		assertThat(BulletinService.videosUrl("https://www.youtube.com/channel/UC123")).isEqualTo("https://www.youtube.com/channel/UC123/videos");
	}
}
