package com.example.demo.finance;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentLogRepository extends JpaRepository<PaymentLog, Long> {

	@EntityGraph(attributePaths = "payment")
	List<PaymentLog> findTop30ByOrderByPaidDateDescIdDesc();

	@EntityGraph(attributePaths = "payment")
	List<PaymentLog> findByPaymentIdOrderByDueDateDesc(Long paymentId);

	Optional<PaymentLog> findFirstByPaymentIdOrderByDueDateDesc(Long paymentId);

	void deleteByPaymentId(Long paymentId);
}
