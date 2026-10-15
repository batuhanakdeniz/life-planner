package com.example.demo.finance.statement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.regex.Pattern;

import com.example.demo.finance.statement.ParsedStatement.Kind;
import com.example.demo.finance.statement.ParsedStatement.Line;

/**
 * "gg.aa.yyyy AÇIKLAMA ... 1.234,56" biçimindeki satırları okuyan genel ayrıştırıcı. Örneği görülmemiş banka
 * biçimleri için temel; sonuçlar deneysel kabul edilir.
 */
class DateLineParser {

	private static final Pattern TX = Pattern.compile(
			"^(\\d{2})[./](\\d{2})[./](\\d{4})\\s+(.+?)\\s+([+-]?" + TrText.AMOUNT + ")(?:\\s*(?:TL|TRY))?(?:\\s+[+-]?" + TrText.AMOUNT + "(?:\\s*(?:TL|TRY))?)?\\s*$");
	private static final Pattern INSTALLMENT = Pattern.compile("(?:(\\d{1,2})\\s*/\\s*(\\d{1,2})\\s*TAKS|TAKS[İI]T\\s*:?\\s*(\\d{1,2})\\s*/\\s*(\\d{1,2}))",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	private static final Pattern SHORT_DATE = Pattern.compile("(\\d{2})[./](\\d{2})[./](\\d{4})");

	private final String bank;

	DateLineParser(String bank) {
		this.bank = bank;
	}

	ParsedStatement parse(String text) {
		var lines = new ArrayList<Line>();
		var warnings = new ArrayList<String>();
		warnings.add(bank + " ekstre biçimi gerçek bir örnekle doğrulanmadı; işlemleri kontrol et.");
		for (var raw : text.split("\\R")) {
			var line = raw.strip();
			var m = TX.matcher(line);
			if (!m.matches()) {
				continue;
			}
			try {
				var date = LocalDate.of(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
				var desc = m.group(4).strip();
				var amountText = m.group(5);
				var amount = TrText.amount(amountText);
				var folded = TrText.fold(desc);
				Kind kind = Kind.PURCHASE;
				if (folded.contains("ODEME") && (amountText.startsWith("+") || amountText.startsWith("-") || folded.startsWith("ODEME"))) {
					kind = Kind.PAYMENT;
					amount = amount.abs().negate();
				}
				else if (amountText.startsWith("-") || folded.contains("IADE")) {
					kind = Kind.REFUND;
					amount = amount.abs().negate();
				}
				else if (folded.contains("FAIZ") || folded.contains("UCRET") || folded.contains("KKDF") || folded.contains("BSMV")) {
					kind = Kind.FEE;
				}
				Integer no = null;
				Integer count = null;
				var inst = INSTALLMENT.matcher(desc);
				if (inst.find()) {
					no = Integer.parseInt(inst.group(1) != null ? inst.group(1) : inst.group(3));
					count = Integer.parseInt(inst.group(2) != null ? inst.group(2) : inst.group(4));
					kind = Kind.INSTALLMENT;
				}
				BigDecimal remaining = no != null ? amount.multiply(BigDecimal.valueOf(count - no)) : null;
				lines.add(new Line(date, desc, amount, kind, no, count, no != null ? amount.multiply(BigDecimal.valueOf(count)) : null,
						remaining, no != null ? count - no : null, null, null));
			}
			catch (RuntimeException e) {
				warnings.add("Okunamayan satır: " + line);
			}
		}
		return new ParsedStatement(bank, true, last4(text), headerDate(text, "Kesim"), headerDate(text, "Son Ödeme"),
				headerAmount(text, "Dönem Borcu"), null, lines, warnings);
	}

	private static String last4(String text) {
		var m = Pattern.compile("Kart (?:No|Numarası)\\s*:?\\s*[\\d*]{4}[ -]?[\\d*]{4}[ -]?[\\d*]{4}[ -]?(\\d{4})").matcher(text);
		return m.find() ? m.group(1) : null;
	}

	private static LocalDate headerDate(String text, String label) {
		var m = Pattern.compile(Pattern.quote(label) + "[^\\n\\d]{0,30}(\\d{2}[./]\\d{2}[./]\\d{4})").matcher(text);
		if (!m.find()) {
			return null;
		}
		var d = SHORT_DATE.matcher(m.group(1));
		return d.find() ? LocalDate.of(Integer.parseInt(d.group(3)), Integer.parseInt(d.group(2)), Integer.parseInt(d.group(1))) : null;
	}

	private static BigDecimal headerAmount(String text, String label) {
		var m = Pattern.compile(Pattern.quote(label) + "[^\\n\\d]{0,20}(" + TrText.AMOUNT + ")").matcher(text);
		return m.find() ? TrText.amount(m.group(1)) : null;
	}
}
