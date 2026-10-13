package com.example.demo.bulletin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kanallar tablosu ile Obsidian'daki Bülten/Kanallar.md arasında iki yönlü senkron.
 * Son senkrondaki iki tarafın özeti saklanır; yalnızca değişen taraf diğerine aktarılır.
 * İki taraf da değiştiyse veritabanı kazanır ve dosyanın eski hali yedeklenir.
 */
@Component
public class ChannelVaultSync {

	private static final Logger log = LoggerFactory.getLogger(ChannelVaultSync.class);

	public enum Outcome { DISABLED, IN_SYNC, FILE_CREATED, IMPORTED_FROM_FILE, WRITTEN_FROM_DB, CONFLICT_DB_WON }

	public record Result(Outcome outcome, List<String> warnings) {
	}

	private final BulletinChannelRepository channels;
	private final ChannelDigestRepository digests;
	private final ChannelSyncStateRepository state;
	private final boolean enabled;
	private final Path file;

	public ChannelVaultSync(BulletinChannelRepository channels, ChannelDigestRepository digests, ChannelSyncStateRepository state,
			@Value("${lifeplanner.vault.enabled:true}") boolean enabled,
			@Value("${lifeplanner.vault.path}") String vaultPath) {
		this.channels = channels;
		this.digests = digests;
		this.state = state;
		this.enabled = enabled;
		this.file = Path.of(vaultPath, "Bülten", "Kanallar.md");
	}

	/**
	 * Her zaman kendi işleminde çalışır: işlem-sonrası olay dinleyicisinden (AFTER_COMMIT) çağrıldığında
	 * REQUIRED yayılımı bitmiş işleme katılır ve yazılanlar kaydedilmez; bu da sahte çakışmalara yol açar.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public synchronized Result sync() {
		if (!enabled) {
			return new Result(Outcome.DISABLED, List.of());
		}
		try {
			var dbRows = dbRows();
			var dbHash = ChannelTable.hash(dbRows);
			var st = state.findById(1L).orElseGet(ChannelSyncState::new);

			if (!Files.exists(file)) {
				write(dbRows);
				save(st, dbHash, dbHash);
				return new Result(Outcome.FILE_CREATED, List.of());
			}
			var parsed = ChannelTable.parse(Files.readString(file, StandardCharsets.UTF_8));
			var fileHash = ChannelTable.hash(parsed.rows());

			if (dbHash.equals(fileHash)) {
				save(st, dbHash, fileHash);
				return new Result(Outcome.IN_SYNC, parsed.warnings());
			}
			boolean fileChanged = !fileHash.equals(st.getFileHash());
			boolean dbChanged = !dbHash.equals(st.getDbHash());

			if (fileChanged && !dbChanged || st.getSyncedAt() == null) {
				// Kullanıcı dosyayı düzenledi (ya da ilk senkron ve dosya önceden vardı)
				importRows(parsed.rows());
				var newHash = ChannelTable.hash(dbRows());
				save(st, newHash, fileHash);
				log.info("Kanallar.md'den {} kanal içe aktarıldı", parsed.rows().size());
				return new Result(Outcome.IMPORTED_FROM_FILE, parsed.warnings());
			}
			if (fileChanged) {
				// İki taraf da değişti: veritabanı kazanır, dosyanın kullanıcı hali yedeklenir
				var backup = file.resolveSibling("Kanallar.cakisma-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
						.withZone(BulletinService.TR).format(Instant.now()) + ".md");
				Files.copy(file, backup);
				write(dbRows);
				save(st, dbHash, dbHash);
				log.warn("Kanallar.md ve veritabanı birlikte değişmiş; veritabanı esas alındı, dosya yedeklendi: {}", backup);
				return new Result(Outcome.CONFLICT_DB_WON, List.of("Çakışma: dosyanın önceki hali " + backup.getFileName() + " olarak yedeklendi"));
			}
			write(dbRows);
			save(st, dbHash, dbHash);
			return new Result(Outcome.WRITTEN_FROM_DB, parsed.warnings());
		}
		catch (IOException e) {
			throw new IllegalStateException("Kanallar.md okunamadı/yazılamadı: " + e.getMessage(), e);
		}
	}

	private List<ChannelTable.Row> dbRows() {
		return channels.findAllByOrderByPositionAscIdAsc().stream()
				.map(c -> new ChannelTable.Row(c.getName(), c.getHandle(), c.isActive(), c.getCutoffTime(), c.getLookbackHours()))
				.toList();
	}

	/** Dosyadaki satırları veritabanına uygular: ekler, günceller, dosyada olmayanları siler. */
	private void importRows(List<ChannelTable.Row> rows) {
		var existing = channels.findAll().stream()
				.collect(Collectors.toMap(c -> c.getHandle().toLowerCase(java.util.Locale.ROOT), Function.identity()));
		int pos = 0;
		var keep = new java.util.HashSet<String>();
		for (var r : rows) {
			if (!keep.add(r.key())) {
				continue; // aynı kanal iki kez yazılmışsa ilki geçerli
			}
			var c = existing.get(r.key());
			if (c == null) {
				c = new BulletinChannel();
			}
			c.setName(r.name());
			c.setHandle(r.handle());
			c.setActive(r.active());
			c.setCutoffTime(r.cutoff());
			c.setLookbackHours(r.lookbackHours());
			c.setPosition(pos++);
			channels.save(c);
		}
		existing.forEach((key, c) -> {
			if (!keep.contains(key)) {
				digests.findByChannelId(c.getId()).forEach(d -> d.setChannel(null));
				channels.delete(c);
			}
		});
		channels.flush();
	}

	private void write(List<ChannelTable.Row> rows) throws IOException {
		Files.createDirectories(file.getParent());
		Files.writeString(file, ChannelTable.render(rows), StandardCharsets.UTF_8);
	}

	private void save(ChannelSyncState st, String dbHash, String fileHash) {
		st.setDbHash(dbHash);
		st.setFileHash(fileHash);
		st.setSyncedAt(Instant.now());
		state.save(st);
	}
}
