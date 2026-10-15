package com.example.demo.bulletin;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.demo.common.vault.VaultSyncStateRepository;
import com.example.demo.common.vault.VaultTableSync;

/** Bülten kanalları ile Obsidian'daki Bülten/Kanallar.md arasında iki yönlü senkron. */
@Component
public class ChannelVaultSync extends VaultTableSync<ChannelTable.Row> {

	private final BulletinChannelRepository channels;
	private final ChannelDigestRepository digests;

	public ChannelVaultSync(BulletinChannelRepository channels, ChannelDigestRepository digests, VaultSyncStateRepository state,
			@Value("${lifeplanner.vault.enabled:true}") boolean enabled,
			@Value("${lifeplanner.vault.path}") String vaultPath) {
		super("bulletin-channels", state, enabled, Path.of(vaultPath, "Bülten", "Kanallar.md"));
		this.channels = channels;
		this.digests = digests;
	}

	@Override
	protected List<ChannelTable.Row> dbRows() {
		return channels.findAllByOrderByPositionAscIdAsc().stream()
				.map(c -> new ChannelTable.Row(c.getName(), c.getHandle(), c.isActive(), c.getCutoffTime(), c.getLookbackHours()))
				.toList();
	}

	@Override
	protected void importRows(List<ChannelTable.Row> rows) {
		var existing = channels.findAll().stream()
				.collect(Collectors.toMap(c -> c.getHandle().toLowerCase(Locale.ROOT), Function.identity()));
		int pos = 0;
		var keep = new HashSet<String>();
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
				// Geçmiş bültenler kalır; kanal bağlantısı kopar
				digests.findByChannelId(c.getId()).forEach(d -> d.setChannel(null));
				channels.delete(c);
			}
		});
		channels.flush();
	}

	@Override
	protected String render(List<ChannelTable.Row> rows) {
		return ChannelTable.render(rows);
	}

	@Override
	protected Parsed<ChannelTable.Row> parse(String markdown) {
		var p = ChannelTable.parse(markdown);
		return new Parsed<>(p.rows(), p.warnings());
	}

	@Override
	protected String canonical(ChannelTable.Row row) {
		return ChannelTable.canonical(row);
	}
}
