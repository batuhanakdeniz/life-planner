package com.example.demo.finance;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringPaymentRepository extends JpaRepository<RecurringPayment, Long> {

	List<RecurringPayment> findAllByOrderByActiveDescNextDueDateAsc();

	List<RecurringPayment> findByActiveTrueAndNextDueDateLessThanEqualOrderByNextDueDateAsc(LocalDate date);
}
