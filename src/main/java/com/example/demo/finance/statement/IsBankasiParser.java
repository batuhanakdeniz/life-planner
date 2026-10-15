package com.example.demo.finance.statement;

import org.springframework.stereotype.Component;

/**
 * İş Bankası (Maximum) kredi kartı ekstresi. DENEYSEL: gerçek bir örnekle doğrulanmadı; genel
 * "gg.aa.yyyy açıklama tutar" kalıbıyla okur. İlk gerçek ekstreyle kontrol edilip özelleştirilmeli.
 */
@Component
public class IsBankasiParser implements StatementParser {

	private final DateLineParser delegate = new DateLineParser("İş Bankası");

	@Override
	public boolean supports(String text) {
		var f = TrText.fold(text);
		return f.contains("IS BANKASI") || f.contains("MAXIMUM") || f.contains("MAXIPUAN") || f.contains("ISBANK");
	}

	@Override
	public ParsedStatement parse(String text) {
		return delegate.parse(text);
	}
}
