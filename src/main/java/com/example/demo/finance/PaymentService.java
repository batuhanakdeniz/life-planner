package com.example.demo.finance;

import static java.time.temporal.ChronoUnit.DAYS;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.common.NotFoundException;
import com.example.demo.finance.PaymentDtos.*;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {

	/** Bugün ekranında kaç gün öncesinden gösterilsin. */
	public static final int DUE_SOON_DAYS = 3;

	private final RecurringPaymentRepository payments;
	private final PaymentLogRepository logs;
	private final PortfolioService portfolio;

	@Transactional(readOnly = true)
	public PaymentsOverview overview() {
		var all = payments.findAllByOrderByActiveDescNextDueDateAsc();
		var missing = new TreeSet<Currency>();
		var monthly = BigDecimal.ZERO;
		var responses = all.stream().map(this::toResponse).toList();
		for (var p : responses) {
			if (!p.active()) {
				continue;
			}
			if (p.monthlyTry() == null) {
				missing.add(p.currency());
			}
			else {
				monthly = monthly.add(p.monthlyTry());
			}
		}
		var recent = logs.findTop30ByOrderByPaidDateDescIdDesc();
		var monthStart = LocalDate.now().withDayOfMonth(1);
		var paidThisMonth = recent.stream().filter(l -> !l.getPaidDate().isBefore(monthStart))
				.map(l -> toTry(l.getAmount(), l.getCurrency()).orElse(BigDecimal.ZERO))
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		return new PaymentsOverview(monthly.setScale(2, RoundingMode.HALF_UP), List.copyOf(missing),
				paidThisMonth.setScale(2, RoundingMode.HALF_UP), responses,
				recent.stream().map(PaymentService::toResponse).toList());
	}

	/** Vadesi geçmiş ya da {@value #DUE_SOON_DAYS} gün içinde gelecek aktif ödemeler. */
	@Transactional(readOnly = true)
	public List<PaymentResponse> dueSoon(LocalDate date) {
		return payments.findByActiveTrueAndNextDueDateLessThanEqualOrderByNextDueDateAsc(date.plusDays(DUE_SOON_DAYS))
				.stream().map(this::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public List<PaymentLogResponse> history(Long paymentId) {
		return logs.findByPaymentIdOrderByDueDateDesc(paymentId).stream().map(PaymentService::toResponse).toList();
	}

	public PaymentResponse create(PaymentRequest req) {
		var p = new RecurringPayment();
		apply(p, req);
		return toResponse(payments.save(p));
	}

	public PaymentResponse update(Long id, PaymentRequest req) {
		var p = get(id);
		apply(p, req);
		return toResponse(p);
	}

	public void delete(Long id) {
		var p = get(id);
		logs.deleteByPaymentId(id);
		payments.delete(p);
	}

	/** Mevcut vadeyi ödendi olarak kaydeder ve vadeyi bir dönem ileri alır. */
	public PaymentResponse pay(Long id, PayRequest req) {
		var p = get(id);
		var log = new PaymentLog();
		log.setPayment(p);
		log.setDueDate(p.getNextDueDate());
		log.setPaidDate(req != null && req.paidDate() != null ? req.paidDate() : LocalDate.now());
		log.setAmount(req != null && req.amount() != null ? req.amount() : p.getAmount());
		log.setCurrency(p.getCurrency());
		logs.save(log);
		p.setNextDueDate(p.getFrequency().next(p.getNextDueDate()));
		return toResponse(p);
	}

	/** Bir ödeme kaydını siler; en son ödeme geri alınıyorsa vade de geri alınır. */
	public void deleteLog(Long logId) {
		var log = logs.findById(logId).orElseThrow(() -> new NotFoundException("Ödeme kaydı", logId));
		var p = log.getPayment();
		var latest = logs.findFirstByPaymentIdOrderByDueDateDesc(p.getId());
		if (latest.isPresent() && latest.get().getId().equals(logId)) {
			p.setNextDueDate(log.getDueDate());
		}
		logs.delete(log);
	}

	private RecurringPayment get(Long id) {
		return payments.findById(id).orElseThrow(() -> new NotFoundException("Ödeme", id));
	}

	private void apply(RecurringPayment p, PaymentRequest req) {
		p.setName(req.name());
		p.setCategory(req.category());
		p.setAmount(req.amount());
		p.setCurrency(req.currency());
		p.setFrequency(req.frequency());
		p.setNextDueDate(req.nextDueDate());
		p.setNote(req.note());
		if (req.active() != null) {
			p.setActive(req.active());
		}
	}

	private Optional<BigDecimal> toTry(BigDecimal amount, Currency currency) {
		return portfolio.rateTry(currency).map(rate -> amount.multiply(rate));
	}

	private PaymentResponse toResponse(RecurringPayment p) {
		var monthly = toTry(p.getAmount().multiply(p.getFrequency().perMonth()), p.getCurrency())
				.map(v -> v.setScale(2, RoundingMode.HALF_UP)).orElse(null);
		var lastPaid = logs.findFirstByPaymentIdOrderByDueDateDesc(p.getId()).map(PaymentLog::getPaidDate).orElse(null);
		return new PaymentResponse(p.getId(), p.getName(), p.getCategory(), p.getAmount(), p.getCurrency(),
				p.getFrequency(), p.getNextDueDate(), DAYS.between(LocalDate.now(), p.getNextDueDate()), p.isActive(),
				p.getNote(), monthly, lastPaid);
	}

	private static PaymentLogResponse toResponse(PaymentLog l) {
		return new PaymentLogResponse(l.getId(), l.getPayment().getId(), l.getPayment().getName(), l.getDueDate(),
				l.getPaidDate(), l.getAmount(), l.getCurrency());
	}
}
