package com.example.demo.finance.statement;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.example.demo.finance.statement.ParsedStatement.Kind;
import com.example.demo.finance.statement.ParsedStatement.Line;

/**
 * Yapı Kredi (World) kredi kartı ekstresi. Gerçek bir ekstreyle doğrulandı (Ekim 2026).
 *
 * <pre>
 * 14 Eylül 2026 SHELL ATAŞEHİR ...   İSTANBUL     TR 3.752,22 150        ← tarih, açıklama, tutar, puan
 * 13 Eylül 2026 HEPSIPAY *HEPSIBURADA ISTANBUL TR 983,00 1.966,00 / 2 59 ← + kalan tutar / kalan taksit
 * 2.949,00 TL'lik işlemin 1 / 3 taksidi                                    ← önceki satırın taksit bilgisi
 * İşlem Tutarı: 240,00 USD                                                 ← önceki satırın döviz tutarı
 * 14 Eylül 2026 ÖDEME-İNTERNET BANKACILIĞI +149.420,19                     ← ödeme
 * </pre>
 */
@Component
public class YapiKrediParser implements StatementParser {

	static final String BANK = "Yapı Kredi";

	private static final Pattern TX = Pattern.compile("^(\\d{1,2}) (\\p{L}+) (\\d{4}) (.+?)\\s+([+-]?" + TrText.AMOUNT + ")"
			+ "(?:\\s+(" + TrText.AMOUNT + ")\\s*/\\s*(\\d+))?(?:\\s+\\d{1,3}(?:\\.\\d{3})*)?\\s*$");
	private static final Pattern INSTALLMENT = Pattern.compile("^(" + TrText.AMOUNT + ") TL'lik işlemin (\\d+) / (\\d+) taksidi");
	private static final Pattern FOREIGN = Pattern.compile("^İşlem Tutarı: (" + TrText.AMOUNT + ") ([A-Z]{3})\\b");
	private static final Pattern HEADER_DATE = Pattern.compile("(\\d{1,2}) (\\p{L}+) (\\d{4})");

	@Override
	public boolean supports(String text) {
		var f = TrText.fold(text);
		return f.contains("YAPI VE KREDI BANKASI") || f.contains("WORLDPUAN");
	}

	@Override
	public ParsedStatement parse(String text) {
		var lines = new ArrayList<Line>();
		var warnings = new ArrayList<String>();
		boolean inTable = false;
		for (var raw : text.split("\\R")) {
			var line = raw.strip();
			if (line.startsWith("İşlem Tarihi İşlemler")) {
				inTable = true;
				continue;
			}
			if (!inTable) {
				continue;
			}
			if (line.startsWith("TOPLAM ")) {
				inTable = false;
				continue;
			}
			var inst = INSTALLMENT.matcher(line);
			if (inst.find() && !lines.isEmpty()) {
				var last = lines.remove(lines.size() - 1);
				lines.add(last.withInstallment(Integer.parseInt(inst.group(2)), Integer.parseInt(inst.group(3)), TrText.amount(inst.group(1))));
				continue;
			}
			var fx = FOREIGN.matcher(line);
			if (fx.find() && !lines.isEmpty()) {
				if (!"TRY".equals(fx.group(2))) {
					var last = lines.remove(lines.size() - 1);
					lines.add(last.withForeign(TrText.amount(fx.group(1)), fx.group(2)));
				}
				continue;
			}
			var m = TX.matcher(line);
			if (m.matches()) {
				try {
					lines.add(toLine(m));
				}
				catch (RuntimeException e) {
					warnings.add("Okunamayan satır: " + line + " (" + e.getMessage() + ")");
				}
			}
			// Diğer satırlar (abone no, sayfa altı, "Temassız İşlem" vb.) bilerek atlanır
		}
		return new ParsedStatement(BANK, false, last4(text), headerDate(text, "Hesap Kesim Tarihi"), headerDate(text, "Son Ödeme Tarihi"),
				headerAmount(text, "Dönem Borcu"), headerAmount(text, "Kalan Toplam Taksit Tutarı"), lines, warnings);
	}

	private static Line toLine(Matcher m) {
		var date = TrText.longDate(m.group(1), m.group(2), m.group(3));
		var desc = m.group(4).strip();
		var amountText = m.group(5);
		var amount = TrText.amount(amountText);
		var folded = TrText.fold(desc);
		Kind kind;
		if (amountText.startsWith("+") || folded.startsWith("ODEME")) {
			kind = Kind.PAYMENT;
			amount = amount.negate();
		}
		else if (amountText.startsWith("-") || folded.contains("IADE")) {
			kind = Kind.REFUND;
			amount = amount.abs().negate();
		}
		else if (folded.contains("FAIZ") || folded.contains("UCRET") || folded.contains("KKDF") || folded.contains("BSMV")) {
			kind = Kind.FEE;
		}
		else {
			kind = Kind.PURCHASE;
		}
		BigDecimal remaining = m.group(6) != null ? TrText.amount(m.group(6)) : null;
		Integer remainingCount = m.group(7) != null ? Integer.parseInt(m.group(7)) : null;
		return new Line(date, desc, amount, kind, null, null, null, remaining, remainingCount, null, null);
	}

	private static String last4(String text) {
		// "5400 61** **** 8700" → son dört hane
		var m = Pattern.compile("Kart Numarası\\s*:\\s*[\\d*]{4} [\\d*]{4} [\\d*]{4} (\\d{4})").matcher(text);
		return m.find() ? m.group(1) : null;
	}

	private static java.time.LocalDate headerDate(String text, String label) {
		var m = Pattern.compile(Pattern.quote(label) + "\\s*:\\s*(\\d{1,2} \\p{L}+ \\d{4})").matcher(text);
		if (!m.find()) {
			return null;
		}
		var d = HEADER_DATE.matcher(m.group(1));
		return d.find() ? TrText.longDate(d.group(1), d.group(2), d.group(3)) : null;
	}

	private static BigDecimal headerAmount(String text, String label) {
		var m = Pattern.compile(Pattern.quote(label) + "\\s*:\\s*(" + TrText.AMOUNT + ")").matcher(text);
		return m.find() ? TrText.amount(m.group(1)) : null;
	}
}
