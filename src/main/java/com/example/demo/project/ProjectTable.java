package com.example.demo.project;

import java.util.ArrayList;
import java.util.List;

import com.example.demo.common.vault.MarkdownTables;
import com.example.demo.common.vault.VaultTableSync.Parsed;

/** Projeler.md içindeki markdown tablosunu okur ve yazar. */
final class ProjectTable {

	record Row(String name, String slug, String description, String platform, String dashboardUrl, boolean active) {
	}

	private ProjectTable() {
	}

	static String render(List<Row> rows) {
		var sb = new StringBuilder();
		sb.append("# Projeler\n\n");
		sb.append("Hayat Planlayıcı'daki Projeler sayfasında görünen projeler. Bu tabloyu düzenleyebilirsin; ");
		sb.append("değişiklikler en geç 5 dakika içinde uygulamaya aktarılır.\n\n");
		sb.append("- **Kod:** projenin kısa, benzersiz adı (ör. `kampus-pazari`). Boş bırakılırsa addan üretilir.\n");
		sb.append("- **Dashboard:** projenin kendi dashboard'unun bağlantısı\n");
		sb.append("- **Aktif:** evet / hayır\n");
		sb.append("- Satırı silmek projeyi listeden çıkarır.\n\n");
		sb.append("| Ad | Kod | Açıklama | Platform | Dashboard | Aktif |\n");
		sb.append("|---|---|---|---|---|---|\n");
		for (var r : rows) {
			sb.append("| ").append(MarkdownTables.escape(r.name()))
					.append(" | ").append(r.slug())
					.append(" | ").append(MarkdownTables.escape(r.description()))
					.append(" | ").append(MarkdownTables.escape(r.platform()))
					.append(" | ").append(MarkdownTables.escape(r.dashboardUrl()))
					.append(" | ").append(r.active() ? "evet" : "hayır").append(" |\n");
		}
		return sb.toString();
	}

	static Parsed<Row> parse(String markdown) {
		var rows = new ArrayList<Row>();
		var warnings = new ArrayList<String>();
		for (var cells : MarkdownTables.rows(markdown, "Kod")) {
			var name = MarkdownTables.cell(cells, 0);
			var slugCell = MarkdownTables.cell(cells, 1);
			if (name.isBlank() && slugCell.isBlank()) {
				continue;
			}
			try {
				var slug = ProjectService.slugify(slugCell.isBlank() ? name : slugCell);
				rows.add(new Row(name.isBlank() ? slug : name, slug, blankToNull(MarkdownTables.cell(cells, 2)),
						blankToNull(MarkdownTables.cell(cells, 3)), blankToNull(MarkdownTables.cell(cells, 4)),
						MarkdownTables.parseBool(MarkdownTables.cell(cells, 5))));
			}
			catch (RuntimeException e) {
				warnings.add("Okunamayan satır (| " + String.join(" | ", cells) + " |): " + e.getMessage());
			}
		}
		return new Parsed<>(rows, warnings);
	}

	static String canonical(Row r) {
		return r.slug() + '|' + r.name() + '|' + r.description() + '|' + r.platform() + '|' + r.dashboardUrl() + '|' + r.active();
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
