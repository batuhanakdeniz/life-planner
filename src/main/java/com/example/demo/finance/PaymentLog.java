package com.example.demo.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Yapılmış bir ödeme kaydı. */
@Entity
@Table(name = "payment_logs")
@Getter
@Setter
@NoArgsConstructor
public class PaymentLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private RecurringPayment payment;

	/** Hangi vadenin ödendiği. */
	@Column(nullable = false)
	private LocalDate dueDate;

	@Column(nullable = false)
	private LocalDate paidDate;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 3)
	private Currency currency;
}
