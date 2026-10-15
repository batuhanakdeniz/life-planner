package com.example.demo.finance.statement;

import java.util.List;

import org.springframework.stereotype.Component;

/** Metne bakarak uygun banka okuyucusunu seçer; hiçbiri uymazsa genel okuyucuyu kullanır. */
@Component
public class StatementParsers {

	private final List<StatementParser> parsers;
	private final DateLineParser fallback = new DateLineParser("Bilinmeyen banka");

	public StatementParsers(List<StatementParser> parsers) {
		this.parsers = parsers;
	}

	public ParsedStatement parse(String text) {
		return parsers.stream().filter(p -> p.supports(text)).findFirst()
				.map(p -> p.parse(text))
				.orElseGet(() -> fallback.parse(text));
	}
}
