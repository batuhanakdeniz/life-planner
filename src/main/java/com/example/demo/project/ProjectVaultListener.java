package com.example.demo.project;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Örnek projeleri ilk açılışta ekler; Obsidian senkronunu açılışta, 5 dakikada bir ve değişiklikte tetikler. */
@Component
public class ProjectVaultListener {

	private final ProjectService service;
	private final ProjectVaultSync sync;

	public ProjectVaultListener(ProjectService service, ProjectVaultSync sync) {
		this.service = service;
		this.sync = sync;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onStartup() {
		// Kullanıcı Projeler.md'yi önceden doldurduysa örnekler eklenmez; dosya içe aktarılır
		if (!java.nio.file.Files.exists(sync.file())) {
			service.seedExamplesIfEmpty();
		}
		sync.sync();
	}

	@Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT5M")
	public void periodic() {
		sync.sync();
	}

	@TransactionalEventListener
	public void onChanged(ProjectService.ProjectsChanged e) {
		sync.sync();
	}
}
