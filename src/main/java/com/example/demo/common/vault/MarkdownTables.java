package com.example.demo.common.vault;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Obsidian markdown tablolarını okuma yardımcıları. */
public final class MarkdownTables {

	private MarkdownTables() {
	}

	/**
	 * Başlığında {@code keyHeader} sütunu bulunan ilk tablonun veri satırlarını (hücre listesi olarak) döner.
	 * Ayraç satırları atlanır; "\|" hücre içinde düz "|" sayılır.
	 */
	public static List<List<String>> rows(String markdown, String keyHeader) {
		var result = new ArrayList<List<String>>();
		boolean inTable = false;
		boolean found = false;
		for (var line : markdown.split("\\R")) {
			var t = line.trim();
			if (!t.startsWith("|")) {
				if (found && inTable) {
					break; // ilk eşleşen tablo bitti
				}
				inTable = false;
				continue;
			}
			var cells = cells(t);
			if (!inTable) {
				inTable = !found && cells.stream().anyMatch(c -> c.equalsIgnoreCase(keyHeader));
				found |= inTable;
				continue;
			}
			if (cells.stream().allMatch(c -> c.matches(":?-+:?"))) {
				continue;
			}
			result.add(cells);
		}
		return result;
	}

	public static List<String> cells(String line) {
		var inner = line.substring(1, line.endsWith("|") ? line.length() - 1 : line.length());
		var cells = new ArrayList<String>();
		for (var c : inner.split("(?<!\\\\)\\|", -1)) {
			cells.add(c.trim().replace("\\|", "|"));
		}
		return cells;
	}

	public static String escape(String s) {
		return s == null ? "" : s.replace("|", "\\|").replace("\n", " ");
	}

	public static boolean parseBool(String s) {
		return switch (s.trim().toLowerCase(Locale.forLanguageTag("tr"))) {
			case "evet", "e", "true", "1", "aktif", "açık", "x", "✓", "" -> true;
			case "hayır", "hayir", "h", "false", "0", "pasif", "kapalı" -> false;
			default -> throw new IllegalArgumentException("Aktif sütunu 'evet' ya da 'hayır' olmalı");
		};
	}

	public static String cell(List<String> cells, int i) {
		return i < cells.size() ? cells.get(i) : "";
	}
}
