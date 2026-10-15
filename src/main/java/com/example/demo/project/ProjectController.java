package com.example.demo.project;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.example.demo.common.vault.VaultTableSync;
import com.example.demo.project.ProjectDtos.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

	private final ProjectService service;
	private final ProjectVaultSync sync;

	public ProjectController(ProjectService service, ProjectVaultSync sync) {
		this.service = service;
		this.sync = sync;
	}

	/** Projeler sayfası: toplamlar ve proje özetleri. */
	@GetMapping
	public Overview overview() {
		return service.overview();
	}

	/** Detay paneli: özet + son 30 günün günlük verisi. */
	@GetMapping("/{slug}")
	public ProjectDetail detail(@PathVariable String slug) {
		return service.detail(slug);
	}

	// --- Proje tanımları ---
	@GetMapping("/catalog")
	public List<ProjectInfo> catalog() {
		return service.list();
	}

	@PostMapping("/catalog")
	@ResponseStatus(HttpStatus.CREATED)
	public ProjectInfo create(@Valid @RequestBody ProjectRequest req) {
		return service.create(req);
	}

	@PutMapping("/catalog/{slug}")
	public ProjectInfo update(@PathVariable String slug, @Valid @RequestBody ProjectRequest req) {
		return service.update(slug, req);
	}

	@DeleteMapping("/catalog/{slug}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable String slug) {
		service.delete(slug);
	}

	/** Projeler.md ile hemen senkron yap. */
	@PostMapping("/catalog/sync")
	public VaultTableSync.Result syncNow() {
		return sync.sync();
	}
}
