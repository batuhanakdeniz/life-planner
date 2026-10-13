package com.example.demo.bulletin;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Obsidian senkronunu tetikler: açılışta, 5 dakikada bir ve değişiklik olduğunda. */
@Component
public class BulletinVaultListener {

	private final ChannelVaultSync sync;
	private final BulletinService bulletins;
	private final BulletinNoteWriter notes;

	public BulletinVaultListener(ChannelVaultSync sync, BulletinService bulletins, BulletinNoteWriter notes) {
		this.sync = sync;
		this.bulletins = bulletins;
		this.notes = notes;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onStartup() {
		sync.sync();
	}

	/** Kullanıcı Kanallar.md'yi ya da tabloyu doğrudan (psql vb.) değiştirmiş olabilir. */
	@Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT5M")
	public void periodic() {
		sync.sync();
	}

	@TransactionalEventListener
	public void onChannelsChanged(BulletinService.ChannelsChanged e) {
		sync.sync();
	}

	@TransactionalEventListener
	public void onBulletinSaved(BulletinService.BulletinSaved e) {
		bulletins.find(e.date()).ifPresent(notes::write);
	}
}
