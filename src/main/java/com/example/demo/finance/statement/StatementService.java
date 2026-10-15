package com.example.demo.finance.statement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.common.NotFoundException;
import com.example.demo.common.upload.DocumentText;
import com.example.demo.common.upload.UploadService;
import com.example.demo.finance.statement.ParsedStatement.Kind;
import com.example.demo.finance.statement.StatementDtos.*;
import com.example.demo.finance.statement.StatementTransaction.CategorySource;

@Service
@Transactional
public class StatementService {

	static final String PURPOSE = "bank-statement";

	private final UploadService uploads;
	private final StatementParsers parsers;
	private final StatementRepository statements;
	private final StatementTransactionRepository transactions;
	private final SpendingCategoryRepository categories;
	private final CategoryRuleRepository rules;

	public StatementService(UploadService uploads, StatementParsers parsers, StatementRepository statements,
			StatementTransactionRepository transactions, SpendingCategoryRepository categories, CategoryRuleRepository rules) {
		this.uploads = uploads;
		this.parsers = parsers;
		this.statements = statements;
		this.transactions = transactions;
		this.categories = categories;
		this.rules = rules;
	}

	// ---------- Yükleme ----------
	public StatementSummary upload(MultipartFile file) {
		var upload = uploads.store(file, PURPOSE);
		if (upload.duplicate()) {
			var existing = statements.findByFileId(upload.file().getId());
			if (existing.isPresent()) {
				return summary(existing.get(), true);
			}
		}
		ParsedStatement parsed;
		try {
			parsed = parsers.parse(DocumentText.pdf(uploads.read(upload.file())));
			if (parsed.lines().isEmpty()) {
				throw new IllegalArgumentException("Bu PDF'te kart işlemi bulunamadı. Banka ekstresi olduğundan emin ol; "
						+ "bankan henüz desteklenmiyorsa haber ver.");
			}
		}
		catch (RuntimeException e) {
			uploads.delete(upload.file().getId()); // okunamayan dosya yetim kalmasın
			throw e;
		}

		var s = new Statement();
		s.setFile(upload.file());
		s.setBank(parsed.bank());
		s.setExperimental(parsed.experimental());
		s.setCardLast4(parsed.cardLast4());
		s.setPeriodEnd(parsed.periodEnd());
		s.setDueDate(parsed.dueDate());
		s.setTotalDue(parsed.totalDue());
		s.setRemainingInstallmentsTotal(parsed.remainingInstallmentsTotal());
		var allRules = rules.findAll();
		for (var l : parsed.lines()) {
			var t = new StatementTransaction();
			t.setStatement(s);
			t.setDate(l.date());
			t.setDescription(trim(l.description(), 255));
			t.setMerchant(trim(Merchants.clean(l.description()), 255));
			t.setAmount(l.amount());
			t.setKind(l.kind());
			t.setInstallmentNo(l.installmentNo());
			t.setInstallmentCount(l.installmentCount());
			t.setPurchaseTotal(l.purchaseTotal());
			t.setRemainingAmount(l.remainingAmount());
			t.setRemainingInstallments(l.remainingInstallments());
			t.setForeignAmount(l.foreignAmount());
			t.setForeignCurrency(l.foreignCurrency());
			applyRules(t, allRules);
			s.getTransactions().add(t);
		}
		var warnings = new ArrayList<>(parsed.warnings());
		var check = check(s);
		if (check != null && !check.matches()) {
			warnings.add("Okunan harcama toplamı (" + check.parsed() + " TL) ekstredeki dönem borcuyla (" + check.onStatement()
					+ " TL) tutmuyor; bazı satırlar okunamamış olabilir.");
		}
		s.setWarnings(warnings.isEmpty() ? null : String.join("\n", warnings));
		return summary(statements.save(s), false);
	}

	public void delete(Long id) {
		var s = get(id);
		var fileId = s.getFile().getId();
		statements.delete(s);
		statements.flush();
		uploads.delete(fileId);
	}

	// ---------- Görüntüleme ----------
	@Transactional(readOnly = true)
	public List<StatementSummary> list() {
		return statements.findAllByOrderByPeriodEndDescIdDesc().stream().map(s -> summary(s, false)).toList();
	}

	@Transactional(readOnly = true)
	public StatementDetail detail(Long id) {
		var s = get(id);
		var txs = s.getTransactions();
		var warnings = s.getWarnings() == null ? List.<String>of() : Arrays.asList(s.getWarnings().split("\n"));
		return new StatementDetail(summary(s, false), warnings, check(s), breakdown(txs), installments(s),
				txs.stream().map(StatementService::view).toList());
	}

	static boolean isSpending(StatementTransaction t) {
		return t.getKind() != Kind.PAYMENT;
	}

	private StatementSummary summary(Statement s, boolean duplicate) {
		var txs = s.getTransactions();
		var spending = txs.stream().filter(StatementService::isSpending).map(StatementTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
		var payments = txs.stream().filter(t -> !isSpending(t)).map(StatementTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add).negate();
		int unclassified = (int) txs.stream().filter(StatementService::isSpending).filter(t -> t.getCategory() == null).count();
		return new StatementSummary(s.getId(), s.getBank(), s.isExperimental(), s.getCardLast4(), s.getPeriodEnd(), s.getDueDate(),
				s.getTotalDue(), spending, payments, txs.size(), unclassified, s.getFile().getOriginalName(), s.getFile().getId(),
				s.getFile().getUploadedAt(), duplicate);
	}

	static TotalsCheck check(Statement s) {
		if (s.getTotalDue() == null) {
			return null;
		}
		var parsed = s.getTransactions().stream().filter(StatementService::isSpending).map(StatementTransaction::getAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		return new TotalsCheck(parsed, s.getTotalDue(), parsed.compareTo(s.getTotalDue()) == 0);
	}

	static List<CategoryShare> breakdown(List<StatementTransaction> txs) {
		var spending = txs.stream().filter(StatementService::isSpending).toList();
		var total = spending.stream().map(StatementTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
		Map<Long, List<StatementTransaction>> groups = new LinkedHashMap<>();
		spending.forEach(t -> groups.computeIfAbsent(t.getCategory() == null ? null : t.getCategory().getId(), k -> new ArrayList<>()).add(t));
		return groups.values().stream().map(list -> {
			var c = list.get(0).getCategory();
			var amount = list.stream().map(StatementTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
			var pct = total.signum() == 0 ? BigDecimal.ZERO : amount.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
			return new CategoryShare(c == null ? null : c.getId(), c == null ? "Sınıflandırılmamış" : c.getName(),
					c == null ? "#334155" : c.getColor(), amount, pct, list.size());
		}).sorted(Comparator.comparing(CategoryShare::amount).reversed()).toList();
	}

	static Installments installments(Statement s) {
		var items = s.getTransactions().stream().filter(t -> t.getInstallmentNo() != null).map(t -> {
			int remaining = t.getRemainingInstallments() != null ? t.getRemainingInstallments()
					: Math.max(t.getInstallmentCount() - t.getInstallmentNo(), 0);
			var remainingAmount = t.getRemainingAmount() != null ? t.getRemainingAmount() : BigDecimal.ZERO;
			var endsIn = s.getPeriodEnd() == null ? null : s.getPeriodEnd().plusMonths(remaining).withDayOfMonth(1);
			return new InstallmentView(t.getId(), t.getMerchant(), t.getDate(), t.getInstallmentNo(), t.getInstallmentCount(),
					t.getAmount(), t.getPurchaseTotal(), remainingAmount, remaining, endsIn,
					t.getCategory() == null ? null : t.getCategory().getId());
		}).sorted(Comparator.comparing(InstallmentView::remainingAmount).reversed()).toList();
		var active = items.stream().filter(i -> i.remainingInstallments() > 0).toList();
		// Kalan tutar / kalan taksit = bir sonraki taksit (son taksitteki kuruş farkı dahil)
		var next = active.stream().map(i -> i.remainingAmount().divide(BigDecimal.valueOf(i.remainingInstallments()), 2, RoundingMode.HALF_UP))
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		var remaining = active.stream().map(InstallmentView::remainingAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
		return new Installments(items, next, remaining, active.size());
	}

	static TransactionView view(StatementTransaction t) {
		return new TransactionView(t.getId(), t.getDate(), t.getMerchant(), t.getDescription(), t.getAmount(), t.getKind(),
				t.getInstallmentNo(), t.getInstallmentCount(), t.getForeignAmount(), t.getForeignCurrency(),
				t.getCategory() == null ? null : t.getCategory().getId(), t.getCategorySource().name(),
				t.getRule() == null ? null : t.getRule().getPattern(), Merchants.suggestPattern(t.getDescription()));
	}

	// ---------- Sınıflandırma ----------
	/** Ödemeler sınıflandırılmaz; diğerlerine en uygun kural uygulanır. MANUAL olanlara dokunmaz. */
	static void applyRules(StatementTransaction t, List<CategoryRule> allRules) {
		if (t.getCategorySource() == CategorySource.MANUAL) {
			return;
		}
		if (t.getKind() == Kind.PAYMENT) {
			t.setCategory(null);
			t.setRule(null);
			t.setCategorySource(CategorySource.NONE);
			return;
		}
		var rule = Categorizer.match(t.getDescription(), allRules);
		t.setRule(rule.orElse(null));
		t.setCategory(rule.map(CategoryRule::getCategory).orElse(null));
		t.setCategorySource(rule.isPresent() ? CategorySource.RULE : CategorySource.NONE);
	}

	/** Tüm (elle değiştirilmemiş) işlemlere kuralları yeniden uygular. */
	int reapplyRules() {
		var allRules = rules.findAll();
		var list = transactions.findByCategorySourceNot(CategorySource.MANUAL);
		list.forEach(t -> applyRules(t, allRules));
		return list.size();
	}

	public TransactionView setCategory(Long txId, SetCategoryRequest req) {
		var t = transactions.findById(txId).orElseThrow(() -> new NotFoundException("İşlem", txId));
		var category = req.categoryId() == null ? null : categories.findById(req.categoryId())
				.orElseThrow(() -> new NotFoundException("Sınıf", req.categoryId()));
		if (req.remember() && category != null) {
			var pattern = TrText.fold(req.pattern() == null || req.pattern().isBlank() ? Merchants.suggestPattern(t.getDescription()) : req.pattern());
			if (pattern.length() < 3) {
				throw new IllegalArgumentException("Kural deseni en az 3 karakter olmalı");
			}
			if (!TrText.fold(t.getDescription()).contains(pattern)) {
				throw new IllegalArgumentException("\"" + pattern + "\" bu işlemin açıklamasında geçmiyor");
			}
			var rule = rules.findByPatternAndUserDefined(pattern, true).orElseGet(CategoryRule::new);
			rule.setPattern(pattern);
			rule.setUserDefined(true);
			rule.setCategory(category);
			rules.save(rule);
			if (t.getCategorySource() == CategorySource.MANUAL) {
				t.setCategorySource(CategorySource.NONE); // kurala bağlansın
			}
			rules.flush();
			reapplyRules();
		}
		else {
			t.setCategory(category);
			t.setRule(null);
			t.setCategorySource(category == null ? CategorySource.NONE : CategorySource.MANUAL);
		}
		return view(t);
	}

	@Transactional(readOnly = true)
	public List<CategoryView> categories() {
		return categories.findAllByOrderByPositionAscIdAsc().stream()
				.map(c -> new CategoryView(c.getId(), c.getName(), c.getColor(), transactions.countByCategoryId(c.getId()))).toList();
	}

	public CategoryView createCategory(CategoryRequest req) {
		if (categories.findByNameIgnoreCase(req.name().trim()).isPresent()) {
			throw new IllegalArgumentException("Bu adla bir sınıf zaten var");
		}
		var c = categories.save(new SpendingCategory(req.name().trim(), req.color() != null ? req.color() : "#94a3b8",
				(int) categories.count()));
		return new CategoryView(c.getId(), c.getName(), c.getColor(), 0);
	}

	public CategoryView updateCategory(Long id, CategoryRequest req) {
		var c = categories.findById(id).orElseThrow(() -> new NotFoundException("Sınıf", id));
		categories.findByNameIgnoreCase(req.name().trim()).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
			throw new IllegalArgumentException("Bu adla bir sınıf zaten var");
		});
		c.setName(req.name().trim());
		if (req.color() != null) {
			c.setColor(req.color());
		}
		return new CategoryView(c.getId(), c.getName(), c.getColor(), transactions.countByCategoryId(id));
	}

	@Transactional(readOnly = true)
	public List<RuleView> userRules() {
		var all = transactions.findAll();
		return rules.findByUserDefinedTrueOrderByCreatedAtDesc().stream()
				.map(r -> new RuleView(r.getId(), r.getPattern(), r.getCategory().getId(), r.getCategory().getName(),
						all.stream().filter(t -> Objects.equals(t.getRule() == null ? null : t.getRule().getId(), r.getId())).count()))
				.toList();
	}

	public RuleView updateRule(Long id, RuleRequest req) {
		var r = rules.findById(id).filter(CategoryRule::isUserDefined).orElseThrow(() -> new NotFoundException("Kural", id));
		var pattern = TrText.fold(req.pattern());
		if (pattern.length() < 3) {
			throw new IllegalArgumentException("Kural deseni en az 3 karakter olmalı");
		}
		r.setPattern(pattern);
		if (req.categoryId() != null) {
			r.setCategory(categories.findById(req.categoryId()).orElseThrow(() -> new NotFoundException("Sınıf", req.categoryId())));
		}
		rules.flush();
		reapplyRules();
		return new RuleView(r.getId(), r.getPattern(), r.getCategory().getId(), r.getCategory().getName(), transactions.findByRuleId(id).size());
	}

	public void deleteRule(Long id) {
		var r = rules.findById(id).filter(CategoryRule::isUserDefined).orElseThrow(() -> new NotFoundException("Kural", id));
		transactions.findByRuleId(id).forEach(t -> t.setRule(null));
		rules.delete(r);
		rules.flush();
		reapplyRules();
	}

	/** İlk açılışta hazır sınıfları ve kuralları ekler. */
	public boolean seedDefaultsIfEmpty() {
		if (categories.count() > 0) {
			return false;
		}
		int pos = 0;
		for (var d : CategoryDefaults.CATEGORIES) {
			var c = categories.save(new SpendingCategory(d.name(), d.color(), pos++));
			for (var p : d.patterns()) {
				var r = new CategoryRule();
				r.setPattern(p); // hazır desenler zaten sadeleştirilmiş; sondaki boşluk korunur ("BIM " → BIMEKS'e uymaz)
				r.setCategory(c);
				r.setUserDefined(false);
				rules.save(r);
			}
		}
		return true;
	}

	private Statement get(Long id) {
		return statements.findById(id).orElseThrow(() -> new NotFoundException("Hesap özeti", id));
	}

	private static String trim(String s, int max) {
		return s.length() > max ? s.substring(0, max) : s;
	}
}
