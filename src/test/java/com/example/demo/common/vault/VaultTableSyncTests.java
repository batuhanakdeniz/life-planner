package com.example.demo.common.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.demo.common.vault.VaultTableSync.Outcome;

class VaultTableSyncTests {

	/** Veritabanı yerine bellekte "anahtar=değer" satırları tutan basit senkron. */
	static final class FakeSync extends VaultTableSync<String> {

		final List<String> db = new ArrayList<>();

		FakeSync(VaultSyncStateRepository state, Path file) {
			super("test", state, true, file);
		}

		@Override
		protected List<String> dbRows() {
			return List.copyOf(db);
		}

		@Override
		protected void importRows(List<String> rows) {
			db.clear();
			db.addAll(rows);
		}

		@Override
		protected String render(List<String> rows) {
			var sb = new StringBuilder("| Kod |\n|---|\n");
			rows.forEach(r -> sb.append("| ").append(r).append(" |\n"));
			return sb.toString();
		}

		@Override
		protected Parsed<String> parse(String markdown) {
			return new Parsed<>(MarkdownTables.rows(markdown, "Kod").stream().map(c -> c.get(0)).toList(), List.of());
		}

		@Override
		protected String canonical(String row) {
			return row;
		}
	}

	@TempDir
	Path dir;

	private FakeSync sync;
	private Path file;

	@BeforeEach
	void setUp() {
		var store = new HashMap<String, VaultSyncState>();
		var repo = mock(VaultSyncStateRepository.class);
		when(repo.findById(anyString())).thenAnswer(i -> Optional.ofNullable(store.get(i.<String>getArgument(0))));
		when(repo.save(any())).thenAnswer(i -> {
			VaultSyncState s = i.getArgument(0);
			store.put(s.getName(), s);
			return s;
		});
		file = dir.resolve("Tablo.md");
		sync = new FakeSync(repo, file);
		sync.db.addAll(List.of("a=1", "b=2"));
	}

	private void writeFile(String... rows) throws Exception {
		Files.writeString(file, sync.render(List.of(rows)));
	}

	@Test
	void createsFileThenStaysInSync() {
		assertThat(sync.sync().outcome()).isEqualTo(Outcome.FILE_CREATED);
		assertThat(file).content().contains("| a=1 |", "| b=2 |");
		assertThat(sync.sync().outcome()).isEqualTo(Outcome.IN_SYNC);
	}

	@Test
	void fileEditIsImportedIntoDb() throws Exception {
		sync.sync();
		writeFile("a=1", "c=3");
		assertThat(sync.sync().outcome()).isEqualTo(Outcome.IMPORTED_FROM_FILE);
		assertThat(sync.db).containsExactly("a=1", "c=3");
		assertThat(sync.sync().outcome()).isEqualTo(Outcome.IN_SYNC);
	}

	@Test
	void dbEditIsWrittenToFile() {
		sync.sync();
		sync.db.add("d=4");
		assertThat(sync.sync().outcome()).isEqualTo(Outcome.WRITTEN_FROM_DB);
		assertThat(file).content().contains("| d=4 |");
	}

	@Test
	void bothChangedDbWinsAndFileIsBackedUp() throws Exception {
		sync.sync();
		sync.db.add("db=yeni");
		writeFile("dosya=yeni");
		var r = sync.sync();
		assertThat(r.outcome()).isEqualTo(Outcome.CONFLICT_DB_WON);
		assertThat(file).content().contains("| db=yeni |").doesNotContain("dosya=yeni");
		try (var files = Files.list(dir)) {
			assertThat(files.filter(f -> f.getFileName().toString().startsWith("Tablo.cakisma-"))).singleElement()
					.satisfies(f -> assertThat(f).content().contains("dosya=yeni"));
		}
	}

	@Test
	void existingFileOnFirstEverSyncWins() throws Exception {
		writeFile("x=9");
		assertThat(sync.sync().outcome()).isEqualTo(Outcome.IMPORTED_FROM_FILE);
		assertThat(sync.db).containsExactly("x=9");
	}
}
