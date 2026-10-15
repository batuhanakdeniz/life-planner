package com.example.demo.project;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.demo.common.vault.VaultSyncStateRepository;
import com.example.demo.common.vault.VaultTableSync;

/** Projeler ile Obsidian'daki Projeler/Projeler.md arasında iki yönlü senkron. */
@Component
public class ProjectVaultSync extends VaultTableSync<ProjectTable.Row> {

	private final ProjectRepository projects;

	public ProjectVaultSync(ProjectRepository projects, VaultSyncStateRepository state,
			@Value("${lifeplanner.vault.enabled:true}") boolean enabled,
			@Value("${lifeplanner.vault.path}") String vaultPath) {
		super("projects", state, enabled, Path.of(vaultPath, "Projeler", "Projeler.md"));
		this.projects = projects;
	}

	@Override
	protected List<ProjectTable.Row> dbRows() {
		return projects.findAllByOrderByPositionAscIdAsc().stream()
				.map(p -> new ProjectTable.Row(p.getName(), p.getSlug(), p.getDescription(), p.getPlatform(), p.getDashboardUrl(), p.isActive()))
				.toList();
	}

	@Override
	protected void importRows(List<ProjectTable.Row> rows) {
		var existing = projects.findAll().stream().collect(Collectors.toMap(Project::getSlug, Function.identity()));
		var keep = new HashSet<String>();
		int pos = 0;
		for (var r : rows) {
			if (!keep.add(r.slug())) {
				continue;
			}
			var p = existing.getOrDefault(r.slug(), new Project());
			p.setSlug(r.slug());
			p.setName(r.name());
			p.setDescription(r.description());
			p.setPlatform(r.platform());
			p.setDashboardUrl(r.dashboardUrl());
			p.setActive(r.active());
			p.setPosition(pos++);
			projects.save(p);
		}
		existing.forEach((slug, p) -> {
			if (!keep.contains(slug)) {
				projects.delete(p);
			}
		});
		projects.flush();
	}

	@Override
	protected String render(List<ProjectTable.Row> rows) {
		return ProjectTable.render(rows);
	}

	@Override
	protected Parsed<ProjectTable.Row> parse(String markdown) {
		return ProjectTable.parse(markdown);
	}

	@Override
	protected String canonical(ProjectTable.Row row) {
		return ProjectTable.canonical(row);
	}
}
