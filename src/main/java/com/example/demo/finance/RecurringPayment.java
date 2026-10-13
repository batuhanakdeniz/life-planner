package com.example.demo.finance;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Kira, fatura, abonelik gibi tekrarlayan ödeme. */
@Entity
@Table(name = "recurring_payments")
@Getter
@Setter
@NoArgsConstructor
public class RecurringPayment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	/** Ör. "Kira", "Fatura", "Abonelik". */
	@Column(length = 64)
	private String category;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 3)
	private Currency currency = Currency.TRY;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Frequency frequency = Frequency.MONTHLY;

	/** Bir sonraki ödemenin vadesi. "Ödendi" işaretlenince bir dönem ileri kayar. */
	@Column(nullable = false)
	private LocalDate nextDueDate;

	private boolean active = true;

	@Column(length = 500)
	private String note;

	@Column(nullable = false, updatable = false)
	private Instant createdAt = Instant.now();
}
