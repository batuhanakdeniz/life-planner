package com.example.demo.finance.statement;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.demo.finance.statement.ParsedStatement.Kind;
import com.example.demo.finance.statement.StatementTransaction.CategorySource;

/** Uydurma (anonim) örnek satırlarla; gerçek ekstreler depoya konmaz. */
class StatementParsingTests {

	/** Yapı Kredi biçimini taklit eden kısa, uydurma ekstre metni. */
	static final String YAPI_KREDI = """
			HESAP BİLGİLERİ
			Hesap Kesim Tarihi : 11 Ekim 2026
			Son Ödeme Tarihi : 21 Ekim 2026
			Dönem Borcu : 5.913,60 TL
			Kalan Toplam Taksit Tutarı : 3.000,00 TL
			Kart Numarası : 1234 56** **** 9876
			İşlem Tarihi İşlemler Tutar(TL) Kalan Tutar/Taksit Puan
			ÖNCEKİ DÖNEM HESAP ÖZETİ BORCU 1.000,00
			14 Eylül 2026 ÖDEME-İNTERNET BANKACILIĞI +1.000,00
			14 Eylül 2026 ORNEK AKARYAKIT SHELL      İSTANBUL     TR 1.752,22 150
			15 Eylül 2026 UBER EATS YEMEK          ISTANBUL     TR 800,00 16
			16 Eylül 2026 BILINMEYEN DUKKAN A.S.   ANKARA       TR 361,30
			04 Ekim 2026 ANTHROPIC* CLAUDE SUB  SAN FRANCISCOCAUS 1.000,00
			İşlem Tutarı: 20,00 USD
			05 Ekim 2026 ENERJISA AYESAŞ SATIŞ 375,00
			ABNO 0000000000                FTNO 0000000000
			   Temassız İşlem
			YAPI ve KREDİ BANKASI A.Ş.
			1 / 2     993
			İşlem Tarihi İşlemler Tutar(TL) Kalan Tutar/Taksit Puan
			11 Ekim 2026 LİMİT AŞIM FAİZİ 5,08
			12 Mayıs 2026 A101 MARKET              ISTANBUL     TR 1.000,00 1.000,00 / 1
			6.000,00 TL'lik işlemin 5 / 6 taksidi
			02 Ekim 2026 ORNEK SIGORTA A.S.       ISTANBUL     TR 500,00 2.000,00 / 4 3.125
			2.500,00 TL'lik işlemin 1 / 5 taksidi
			28 Şubat 2026 MUDO MUDO KOZYATAĞI      İSTANBUL     TR 120,00
			960,00 TL'lik işlemin 8 / 8 taksidi
			TOPLAM 5.913,60 3743
			PUAN ÖZETİ
			""";

	@Test
	void parsesYapiKrediStatement() {
		var parser = new YapiKrediParser();
		assertThat(parser.supports(YAPI_KREDI)).isTrue();
		var s = parser.parse(YAPI_KREDI);

		assertThat(s.bank()).isEqualTo("Yapı Kredi");
		assertThat(s.experimental()).isFalse();
		assertThat(s.cardLast4()).isEqualTo("9876");
		assertThat(s.periodEnd()).isEqualTo(LocalDate.of(2026, 10, 11));
		assertThat(s.dueDate()).isEqualTo(LocalDate.of(2026, 10, 21));
		assertThat(s.totalDue()).isEqualByComparingTo("5913.60");
		assertThat(s.remainingInstallmentsTotal()).isEqualByComparingTo("3000.00");
		assertThat(s.warnings()).isEmpty();
		assertThat(s.lines()).hasSize(10);

		var payment = s.lines().get(0);
		assertThat(payment.kind()).isEqualTo(Kind.PAYMENT);
		assertThat(payment.amount()).isEqualByComparingTo("-1000.00");

		// Sondaki puan (150) tutar sanılmamalı
		assertThat(s.lines().get(1).amount()).isEqualByComparingTo("1752.22");

		var foreign = s.lines().get(4);
		assertThat(foreign.foreignAmount()).isEqualByComparingTo("20.00");
		assertThat(foreign.foreignCurrency()).isEqualTo("USD");

		assertThat(s.lines().get(6).kind()).isEqualTo(Kind.FEE);

		var inst = s.lines().get(8);
		assertThat(inst.kind()).isEqualTo(Kind.INSTALLMENT);
		assertThat(inst.installmentNo()).isEqualTo(1);
		assertThat(inst.installmentCount()).isEqualTo(5);
		assertThat(inst.purchaseTotal()).isEqualByComparingTo("2500.00");
		assertThat(inst.remainingAmount()).isEqualByComparingTo("2000.00");
		assertThat(inst.remainingInstallments()).isEqualTo(4);

		// Harcama toplamı (ödeme hariç) dönem borcuyla tutmalı
		var spending = s.lines().stream().filter(l -> l.kind() != Kind.PAYMENT).map(ParsedStatement.Line::amount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		assertThat(spending).isEqualByComparingTo(s.totalDue());
	}

	@Test
	void genericParserReadsShortDatesAndInstallments() {
		var text = """
				TÜRKİYE İŞ BANKASI A.Ş. Maximum
				Hesap Kesim Tarihi: 10.10.2026
				Son Ödeme Tarihi: 20.10.2026
				Dönem Borcu: 1.350,00 TL
				Kart No: 4543 21** **** 5555
				01.10.2026 MIGROS ISTANBUL 250,00 TL
				03/10/2026 TEKNOSA 2/6 TAKSİT 1.000,00
				05.10.2026 ÖDEME - TEŞEKKÜRLER -500,00
				06.10.2026 IADE ORNEK MAGAZA -100,00 TL
				""";
		var parser = new IsBankasiParser();
		assertThat(parser.supports(text)).isTrue();
		var s = parser.parse(text);
		assertThat(s.experimental()).isTrue();
		assertThat(s.cardLast4()).isEqualTo("5555");
		assertThat(s.periodEnd()).isEqualTo(LocalDate.of(2026, 10, 10));
		assertThat(s.totalDue()).isEqualByComparingTo("1350.00");
		assertThat(s.lines()).extracting(ParsedStatement.Line::kind)
				.containsExactly(Kind.PURCHASE, Kind.INSTALLMENT, Kind.PAYMENT, Kind.REFUND);
		var inst = s.lines().get(1);
		assertThat(inst.installmentNo()).isEqualTo(2);
		assertThat(inst.installmentCount()).isEqualTo(6);
		assertThat(inst.remainingInstallments()).isEqualTo(4);
		assertThat(s.lines().get(2).amount()).isEqualByComparingTo("-500.00");
	}

	@Test
	void merchantCleaningKeepsNameWords() {
		assertThat(Merchants.clean("MONEYPAY    *DIJITAL PLATISTANBUL     TR")).isEqualTo("MONEYPAY *DIJITAL PLAT");
		assertThat(Merchants.clean("KZL GIDA               İSTANBUL       TR")).isEqualTo("KZL GIDA");
		assertThat(Merchants.clean("APPLE.COM/BILL         CORK         IRIR")).isEqualTo("APPLE.COM/BILL");
		assertThat(Merchants.clean("SPOTIFY*P477613E49     SPOTIFY.COM  ABSW")).isEqualTo("SPOTIFY*P477613E49");
		assertThat(Merchants.clean("LİMİT AŞIM FAİZİ")).isEqualTo("LİMİT AŞIM FAİZİ");
		assertThat(Merchants.suggestPattern("UBER EATS YEMEK          ISTANBUL     TR")).isEqualTo("UBER EATS");
		assertThat(Merchants.suggestPattern("BOĞAZİÇİ BALIK         İSTANBUL       TR")).isEqualTo("BOGAZICI BALIK");
	}

	private static CategoryRule rule(String pattern, String category, boolean user) {
		var r = new CategoryRule();
		r.setPattern(pattern);
		r.setCategory(new SpendingCategory(category, "#000000", 0));
		r.setUserDefined(user);
		return r;
	}

	@Test
	void longestPatternWinsAndUserRulesComeFirst() {
		var rules = List.of(rule("UBER", "Ulaşım", false), rule("UBER EATS", "Restoran", false), rule("SHELL", "Akaryakıt", false));
		assertThat(Categorizer.match("UBER EATS YEMEK ISTANBUL", rules)).get().extracting(r -> r.getCategory().getName()).isEqualTo("Restoran");
		assertThat(Categorizer.match("UBER *TRIP ISTANBUL", rules)).get().extracting(r -> r.getCategory().getName()).isEqualTo("Ulaşım");
		// Türkçe harfler sadeleştirilir: "ŞELL" yok ama "İSTANBUL"/"ISTANBUL" farkı önemsiz
		assertThat(Categorizer.match("BILINMEYEN", rules)).isEmpty();

		var withUser = new ArrayList<>(rules);
		withUser.add(rule("UBER", "İş seyahati", true));
		assertThat(Categorizer.match("UBER EATS YEMEK", withUser)).get().extracting(r -> r.getCategory().getName()).isEqualTo("İş seyahati");
	}

	private static StatementTransaction tx(String amount, Kind kind, Integer no, Integer count, String remaining, Integer remainingCount,
			SpendingCategory c) {
		var t = new StatementTransaction();
		t.setDate(LocalDate.of(2026, 9, 1));
		t.setDescription("X");
		t.setMerchant("X");
		t.setAmount(new BigDecimal(amount));
		t.setKind(kind);
		t.setInstallmentNo(no);
		t.setInstallmentCount(count);
		t.setRemainingAmount(remaining == null ? null : new BigDecimal(remaining));
		t.setRemainingInstallments(remainingCount);
		t.setCategory(c);
		t.setCategorySource(c == null ? CategorySource.NONE : CategorySource.RULE);
		return t;
	}

	@Test
	void breakdownInstallmentsAndTotalsCheck() {
		var market = new SpendingCategory("Market", "#4ade80", 0);
		market.setId(1L);
		var s = new Statement();
		s.setPeriodEnd(LocalDate.of(2026, 10, 11));
		s.setTotalDue(new BigDecimal("1800.00"));
		s.getTransactions().addAll(List.of(
				tx("-1000.00", Kind.PAYMENT, null, null, null, null, null),
				tx("600.00", Kind.PURCHASE, null, null, null, null, market),
				tx("1000.00", Kind.INSTALLMENT, 2, 3, "1000.01", 1, null),
				tx("200.00", Kind.INSTALLMENT, 4, 4, null, null, market)));

		var shares = StatementService.breakdown(s.getTransactions());
		assertThat(shares).hasSize(2);
		assertThat(shares.get(0).name()).isEqualTo("Sınıflandırılmamış");
		assertThat(shares.get(0).amount()).isEqualByComparingTo("1000.00");
		assertThat(shares.get(1).amount()).isEqualByComparingTo("800.00");
		assertThat(shares.get(1).percent()).isEqualByComparingTo("44.4");

		var inst = StatementService.installments(s);
		assertThat(inst.active()).isEqualTo(1);
		assertThat(inst.nextMonthTotal()).isEqualByComparingTo("1000.01");
		assertThat(inst.remainingTotal()).isEqualByComparingTo("1000.01");
		assertThat(inst.items().get(0).endsIn()).isEqualTo(LocalDate.of(2026, 11, 1));

		var check = StatementService.check(s);
		assertThat(check.matches()).isTrue();
	}

	@Test
	void paymentsAreNeverCategorized() {
		var t = tx("-1000.00", Kind.PAYMENT, null, null, null, null, null);
		t.setDescription("ÖDEME-INTERNET BANKACILIGI");
		StatementService.applyRules(t, List.of(rule("ODEME", "Diğer", false)));
		assertThat(t.getCategory()).isNull();
		assertThat(t.getCategorySource()).isEqualTo(CategorySource.NONE);
	}
}
