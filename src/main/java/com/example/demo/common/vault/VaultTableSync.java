package com.example.demo.common.vault;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bir veritabanı tablosu ile Obsidian'daki bir markdown tablosu arasında iki yönlü senkron.
 * Son senkrondaki iki tarafın özeti saklanır; yalnızca değişen taraf diğerine aktarılır.
 * İki taraf da değiştiyse veritabanı kazanır ve dosyanın kullanıcı hali yedeklenir.
 *
 * @param <R> tablodaki bir satır
 */
public abstract class VaultTableSync<R> {

	private static final Logger log = LoggerFactory.getLogger(VaultTableSync.class);
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.of("Europe/Istanbul"));

	public enum Outcome { DISABLED, IN_SYNC, FILE_CREATED, IMPORTED_FROM_FILE, WRITTEN_FROM_DB, CONFLICT_DB_WON }

	public record Result(Outcome outcome, List<String> warnings) {
	}

	/** Okuma sonucu: geçerli satırlar ve okunamayan satırlar için uyarılar. */
	public record Parsed<R>(List<R> rows, List<String> warnings) {
	}

	private final String name;
	private final VaultSyncStateRepository state;
	private final boolean enabled;
	private final Path file;

	protected VaultTableSync(String name, VaultSyncStateRepository state, boolean enabled, Path file) {
		this.name = name;
		this.state = state;
		this.enabled = enabled;
		this.file = file;
	}

	/** Veritabanındaki satırlar (dosyaya yazılacak sırada). */
	protected abstract List<R> dbRows();

	/** Dosyadaki satırları veritabanına uygular: ekler, günceller, dosyada olmayanları siler. */
	protected abstract void importRows(List<R> rows);

	protected abstract String render(List<R> rows);

	protected abstract Parsed<R> parse(String markdown);

	/** Satırın sıradan bağımsız, karşılaştırılabilir metin hali (özet için). İlk alan anahtar olmalı. */
	protected abstract String canonical(R row);

	public Path file() {
		return file;
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
			var dbHash = hash(dbRows);
			var st = state.findById(name).orElseGet(() -> new VaultSyncState(name));

			if (!Files.exists(file)) {
				write(dbRows);
				save(st, dbHash, dbHash);
				return new Result(Outcome.FILE_CREATED, List.of());
			}
			var parsed = parse(Files.readString(file, StandardCharsets.UTF_8));
			var fileHash = hash(parsed.rows());

			if (dbHash.equals(fileHash)) {
				save(st, dbHash, fileHash);
				return new Result(Outcome.IN_SYNC, parsed.warnings());
			}
			boolean fileChanged = !fileHash.equals(st.getFileHash());
			boolean dbChanged = !dbHash.equals(st.getDbHash());

			if (fileChanged && !dbChanged || st.getSyncedAt() == null) {
				// Kullanıcı dosyayı düzenledi (ya da ilk senkron ve dosya önceden vardı)
				importRows(parsed.rows());
				save(st, hash(dbRows()), fileHash);
				log.info("{}: {} satır içe aktarıldı", file.getFileName(), parsed.rows().size());
				return new Result(Outcome.IMPORTED_FROM_FILE, parsed.warnings());
			}
			if (fileChanged) {
				var base = file.getFileName().toString().replaceFirst("\\.md$", "");
				var backup = file.resolveSibling(base + ".cakisma-" + STAMP.format(Instant.now()) + ".md");
				Files.copy(file, backup);
				write(dbRows);
				save(st, dbHash, dbHash);
				log.warn("{} ve veritabanı birlikte değişmiş; veritabanı esas alındı, dosya yedeklendi: {}", file.getFileName(), backup);
				return new Result(Outcome.CONFLICT_DB_WON, List.of("Çakışma: dosyanın önceki hali " + backup.getFileName() + " olarak yedeklendi"));
			}
			write(dbRows);
			save(st, dbHash, dbHash);
			return new Result(Outcome.WRITTEN_FROM_DB, parsed.warnings());
		}
		catch (IOException e) {
			throw new IllegalStateException(file.getFileName() + " okunamadı/yazılamadı: " + e.getMessage(), e);
		}
	}

	String hash(List<R> rows) {
		return hashOf(rows.stream().map(this::canonical).toList());
	}

	/** Satırların sıradan bağımsız özeti. */
	public static String hashOf(List<String> canonicalRows) {
		var sb = new StringBuilder();
		canonicalRows.stream().sorted().forEach(r -> sb.append(r).append('\n'));
		try {
			var digest = java.security.MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private void write(List<R> rows) throws IOException {
		Files.createDirectories(file.getParent());
		Files.writeString(file, render(rows), StandardCharsets.UTF_8);
	}

	private void save(VaultSyncState st, String dbHash, String fileHash) {
		st.setDbHash(dbHash);
		st.setFileHash(fileHash);
		st.setSyncedAt(Instant.now());
		state.save(st);
	}
}
