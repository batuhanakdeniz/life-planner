package com.example.demo.bulletin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.demo.bulletin.BulletinDtos.BulletinResponse;

/** Her bülteni Obsidian'a Bülten/YYYY-MM-DD.md notu olarak yazar. */
@Component
public class BulletinNoteWriter {

	private static final Logger log = LoggerFactory.getLogger(BulletinNoteWriter.class);
	private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("d MMMM yyyy EEEE", java.util.Locale.forLanguageTag("tr-TR"));
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d MMM HH:mm", java.util.Locale.forLanguageTag("tr-TR"))
			.withZone(BulletinService.TR);

	private final boolean enabled;
	private final Path dir;

	public BulletinNoteWriter(@Value("${lifeplanner.vault.enabled:true}") boolean enabled,
			@Value("${lifeplanner.vault.path}") String vaultPath) {
		this.enabled = enabled;
		this.dir = Path.of(vaultPath, "Bülten");
	}

	public void write(BulletinResponse b) {
		if (!enabled) {
			return;
		}
		try {
			Files.createDirectories(dir);
			Files.writeString(dir.resolve(b.date() + ".md"), render(b), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			log.warn("Bülten notu yazılamadı: {}", e.getMessage());
		}
	}

	static String render(BulletinResponse b) {
		var sb = new StringBuilder();
		sb.append("# Bülten · ").append(b.date().format(LONG)).append("\n\n");
		sb.append("## Günün özeti\n\n");
		sb.append(b.overview() != null ? b.overview().trim() : "_Özet yok._").append("\n\n");
		for (var c : b.channels()) {
			sb.append("## ").append(c.channelName()).append("\n\n");
			sb.append("_").append(TIME.format(c.windowStart())).append(" → ").append(TIME.format(c.windowEnd()))
					.append(" · ").append(c.videos().size()).append(" video_\n\n");
			if (c.error() != null) {
				sb.append("> [!warning] ").append(c.error()).append("\n\n");
			}
			if (c.shortSummary() != null) {
				sb.append(c.shortSummary().trim()).append("\n\n");
			}
			if (c.skippedVideos() > 0) {
				sb.append("_≈").append(c.skippedVideos()).append(" video daha yayınlandı; kanal başına sınır nedeniyle özetlenmedi._\n\n");
			}
			for (var v : c.videos()) {
				sb.append("### [").append(v.title().replace("]", ")")).append("](").append(v.url()).append(")\n\n");
				var meta = new StringBuilder();
				if (v.publishedAt() != null) {
					meta.append(TIME.format(v.publishedAt()));
				}
				if (v.durationSeconds() != null) {
					meta.append(meta.isEmpty() ? "" : " · ").append(Math.round(v.durationSeconds() / 60.0)).append(" dk");
				}
				if (!v.hasTranscript()) {
					meta.append(meta.isEmpty() ? "" : " · ").append("altyazı yok, açıklamadan özetlendi");
				}
				if (!meta.isEmpty()) {
					sb.append("_").append(meta).append("_\n\n");
				}
				if (v.summary() != null) {
					sb.append(v.summary().trim()).append("\n\n");
				}
				v.keyPoints().forEach(p -> sb.append("- ").append(p).append("\n"));
				if (!v.keyPoints().isEmpty()) {
					sb.append("\n");
				}
				if (!v.chapters().isEmpty()) {
					sb.append("**Bölümler**\n\n");
					v.chapters().forEach(ch -> sb.append("- [").append(clock(ch.seconds())).append("](").append(v.url())
							.append("&t=").append(ch.seconds()).append("s) ").append(ch.label()).append("\n"));
					sb.append("\n");
				}
				var m = v.mentions();
				if (m != null) {
					appendList(sb, "Kişiler", m.people());
					appendList(sb, "Şirketler", m.companies());
					appendList(sb, "Rakamlar", m.numbers());
				}
			}
		}
		return sb.toString();
	}

	private static void appendList(StringBuilder sb, String label, java.util.List<String> items) {
		if (items != null && !items.isEmpty()) {
			sb.append("**").append(label).append(":** ").append(String.join(", ", items)).append("\n\n");
		}
	}

	static String clock(int seconds) {
		int h = seconds / 3600;
		int m = seconds % 3600 / 60;
		int s = seconds % 60;
		return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
	}
}
