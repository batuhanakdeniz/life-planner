package com.example.demo.bulletin;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.bulletin.BulletinDtos.*;
import com.example.demo.common.NotFoundException;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional
public class BulletinService {

	public static final ZoneId TR = ZoneId.of("Europe/Istanbul");
	/** Bültenin hazırlandığı saat; kesimi bundan sonra olan kanallar için önceki günün kesimi kullanılır. */
	public static final LocalTime RUN_TIME = LocalTime.of(12, 5);
	/** Kaçırılan günlerden en fazla kaç tanesi telafi edilir. */
	public static final int MAX_CATCH_UP_DAYS = 7;

	private static final JsonMapper JSON = JsonMapper.builder().build();

	/** Kanallar değişti (Obsidian dosyasının yeniden yazılması için). */
	public record ChannelsChanged() {
	}

	/** Bülten kaydedildi (Obsidian notunun yazılması için). */
	public record BulletinSaved(LocalDate date) {
	}

	private final BulletinChannelRepository channels;
	private final BulletinRepository bulletins;
	private final ChannelDigestRepository digests;
	private final ApplicationEventPublisher events;

	public BulletinService(BulletinChannelRepository channels, BulletinRepository bulletins, ChannelDigestRepository digests,
			ApplicationEventPublisher events) {
		this.channels = channels;
		this.bulletins = bulletins;
		this.digests = digests;
		this.events = events;
	}

	// ---------- Kanallar ----------
	@Transactional(readOnly = true)
	public List<ChannelResponse> listChannels() {
		return channels.findAllByOrderByPositionAscIdAsc().stream().map(BulletinService::toResponse).toList();
	}

	public ChannelResponse createChannel(ChannelRequest req) {
		var handle = normalizeHandle(req.handle());
		if (channels.findByHandleIgnoreCase(handle).isPresent()) {
			throw new IllegalArgumentException("Bu kanal zaten listede: " + handle);
		}
		var c = new BulletinChannel();
		c.setPosition((int) channels.count());
		apply(c, req, handle);
		var saved = channels.save(c);
		events.publishEvent(new ChannelsChanged());
		return toResponse(saved);
	}

	public ChannelResponse updateChannel(Long id, ChannelRequest req) {
		var c = channels.findById(id).orElseThrow(() -> new NotFoundException("Kanal", id));
		var handle = normalizeHandle(req.handle());
		channels.findByHandleIgnoreCase(handle).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
			throw new IllegalArgumentException("Bu kanal zaten listede: " + handle);
		});
		apply(c, req, handle);
		events.publishEvent(new ChannelsChanged());
		return toResponse(c);
	}

	public void deleteChannel(Long id) {
		var c = channels.findById(id).orElseThrow(() -> new NotFoundException("Kanal", id));
		// Geçmiş bültenler kalır; kanal bağlantısı kopar, ad ve handle kopyası korunur
		digests.findByChannelId(id).forEach(d -> d.setChannel(null));
		channels.delete(c);
		events.publishEvent(new ChannelsChanged());
	}

	private static void apply(BulletinChannel c, ChannelRequest req, String handle) {
		c.setName(req.name().trim());
		c.setHandle(handle);
		if (req.active() != null) {
			c.setActive(req.active());
		}
		if (req.cutoffTime() != null) {
			c.setCutoffTime(req.cutoffTime());
		}
		if (req.lookbackHours() != null) {
			c.setLookbackHours(req.lookbackHours());
		}
	}

	/** "TEDx" → "@TEDx"; bağlantılar olduğu gibi (sondaki / ve /videos atılarak). */
	static String normalizeHandle(String raw) {
		var h = raw.trim();
		if (h.startsWith("http://") || h.startsWith("https://")) {
			h = h.replaceAll("/+$", "").replaceAll("/(videos|featured|streams|shorts)$", "");
			return h;
		}
		return h.startsWith("@") ? h : "@" + h;
	}

	/** Kanalın video listesinin adresi. */
	static String videosUrl(String handle) {
		var base = handle.startsWith("http") ? handle : "https://www.youtube.com/" + handle;
		return base + "/videos";
	}

	static ChannelResponse toResponse(BulletinChannel c) {
		return new ChannelResponse(c.getId(), c.getName(), c.getHandle(), videosUrl(c.getHandle()), c.isActive(),
				c.getCutoffTime(), c.getLookbackHours());
	}

	// ---------- Zaman aralıkları ----------
	/**
	 * Bülten günü için kanalın tarama aralığı. Bitiş, o günün kesim saatidir; kesim saati bültenin
	 * hazırlandığı saatten (12:05) sonraysa henüz gelmemiş olacağından önceki günün kesimi kullanılır.
	 */
	static ChannelWindow window(BulletinChannel c, LocalDate date) {
		var endDay = c.getCutoffTime().isAfter(RUN_TIME) ? date.minusDays(1) : date;
		var end = ZonedDateTime.of(endDay, c.getCutoffTime(), TR).toInstant();
		var start = end.minus(Duration.ofHours(c.getLookbackHours()));
		return new ChannelWindow(c.getId(), c.getName(), c.getHandle(), videosUrl(c.getHandle()), start, end);
	}

	@Transactional(readOnly = true)
	public RunPlan plan(LocalDate date) {
		var list = channels.findAllByOrderByPositionAscIdAsc().stream().filter(BulletinChannel::isActive)
				.map(c -> window(c, date)).toList();
		return new RunPlan(date, list);
	}

	/**
	 * Hazırlanması gereken bülten günleri (eskiden yeniye). Bugünün bülteni 12:05'ten sonra "vadesi gelmiş"
	 * sayılır. Son bültenden sonra kaçırılan günler en fazla {@value #MAX_CATCH_UP_DAYS} gün geriye telafi edilir.
	 */
	@Transactional(readOnly = true)
	public List<LocalDate> pendingDates(ZonedDateTime now) {
		return pendingDates(now, bulletins.findFirstByOrderByDateDesc().map(Bulletin::getDate));
	}

	static List<LocalDate> pendingDates(ZonedDateTime now, Optional<LocalDate> last) {
		var nowTr = now.withZoneSameInstant(TR);
		var latestDue = nowTr.toLocalTime().isBefore(RUN_TIME) ? nowTr.toLocalDate().minusDays(1) : nowTr.toLocalDate();
		var earliest = latestDue.minusDays(MAX_CATCH_UP_DAYS - 1);
		LocalDate from;
		if (last.isEmpty()) {
			from = latestDue; // İlk kurulum: geçmişe dönük bülten üretme
		}
		else {
			var next = last.get().plusDays(1);
			from = next.isBefore(earliest) ? earliest : next;
		}
		var result = new ArrayList<LocalDate>();
		for (var d = from; !d.isAfter(latestDue); d = d.plusDays(1)) {
			result.add(d);
		}
		return result;
	}

	// ---------- Bülten ----------
	@Transactional(readOnly = true)
	public Optional<BulletinResponse> find(LocalDate date) {
		return (date != null ? bulletins.findByDate(date) : bulletins.findFirstByOrderByDateDesc()).map(BulletinService::toResponse);
	}

	@Transactional(readOnly = true)
	public List<LocalDate> dates() {
		return bulletins.findAllDates();
	}

	public BulletinResponse save(LocalDate date, BulletinPayload payload) {
		var b = bulletins.findByDate(date).orElseGet(() -> {
			var nb = new Bulletin();
			nb.setDate(date);
			return nb;
		});
		b.setOverview(payload.overview());
		b.setGeneratedAt(Instant.now());
		b.getChannels().clear();
		bulletins.saveAndFlush(b);

		int pos = 0;
		for (var cp : payload.channels()) {
			var channel = channels.findById(cp.channelId()).orElseThrow(() -> new NotFoundException("Kanal", cp.channelId()));
			var d = new ChannelDigest();
			d.setBulletin(b);
			d.setChannel(channel);
			d.setChannelName(channel.getName());
			d.setChannelHandle(channel.getHandle());
			d.setShortSummary(blankToNull(cp.shortSummary()));
			d.setError(blankToNull(cp.error()));
			d.setWindowStart(cp.windowStart());
			d.setWindowEnd(cp.windowEnd());
			d.setPosition(pos++);
			d.setSkippedVideos(cp.skippedVideos() != null ? cp.skippedVideos() : 0);
			if (cp.videos() != null) {
				for (var vp : cp.videos()) {
					d.getVideos().add(toEntity(d, vp));
				}
			}
			b.getChannels().add(d);
		}
		var saved = bulletins.save(b);
		events.publishEvent(new BulletinSaved(date));
		return toResponse(saved);
	}

	private static VideoSummary toEntity(ChannelDigest d, VideoPayload vp) {
		var v = new VideoSummary();
		v.setDigest(d);
		v.setVideoId(vp.videoId());
		v.setTitle(vp.title());
		v.setPublishedAt(vp.publishedAt());
		v.setDurationSeconds(vp.durationSeconds());
		v.setHasTranscript(vp.hasTranscript());
		v.setSummary(blankToNull(vp.summary()));
		v.setKeyPointsJson(JSON.writeValueAsString(vp.keyPoints() != null ? vp.keyPoints() : List.of()));
		v.setChaptersJson(JSON.writeValueAsString(vp.chapters() != null ? vp.chapters() : List.of()));
		v.setMentionsJson(JSON.writeValueAsString(vp.mentions() != null ? vp.mentions() : new Mentions(List.of(), List.of(), List.of())));
		return v;
	}

	static BulletinResponse toResponse(Bulletin b) {
		return new BulletinResponse(b.getDate(), b.getOverview(), b.getGeneratedAt(), b.getChannels().stream()
				.map(d -> new ChannelDigestResponse(d.getChannel() != null ? d.getChannel().getId() : null, d.getChannelName(),
						d.getChannelHandle(), d.getShortSummary(), d.getError(), d.getWindowStart(), d.getWindowEnd(),
						d.getSkippedVideos(), d.getVideos().stream().map(BulletinService::toResponse).toList()))
				.toList());
	}

	private static VideoResponse toResponse(VideoSummary v) {
		return new VideoResponse(v.getVideoId(), v.getTitle(), "https://www.youtube.com/watch?v=" + v.getVideoId(),
				v.getPublishedAt(), v.getDurationSeconds(), v.isHasTranscript(), v.getSummary(),
				read(v.getKeyPointsJson(), new TypeReference<List<String>>() { }, List.of()),
				read(v.getChaptersJson(), new TypeReference<List<Chapter>>() { }, List.of()),
				read(v.getMentionsJson(), new TypeReference<Mentions>() { }, new Mentions(List.of(), List.of(), List.of())));
	}

	private static <T> T read(String json, TypeReference<T> type, T fallback) {
		if (json == null || json.isBlank()) {
			return fallback;
		}
		try {
			return JSON.readValue(json, type);
		}
		catch (RuntimeException e) {
			return fallback;
		}
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
