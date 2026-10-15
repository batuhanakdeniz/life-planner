package com.example.demo.finance.statement;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.example.demo.finance.statement.ParsedStatement.Kind;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ekstredeki bir işlem satırı. */
@Entity
@Table(name = "statement_transactions", indexes = @Index(columnList = "statement_id"))
@Getter
@Setter
@NoArgsConstructor
public class StatementTransaction {

	/** Sınıfın nereden geldiği. MANUAL olanlara kural değişiklikleri dokunmaz. */
	public enum CategorySource { RULE, MANUAL, NONE }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Statement statement;

	@Column(nullable = false)
	private LocalDate date;

	@Column(nullable = false, length = 255)
	private String description;

	/** Görüntülenecek sadeleştirilmiş iş yeri adı. */
	@Column(nullable = false, length = 255)
	private String merchant;

	/** Bu ekstreye yansıyan tutar (TL); ödeme ve iadelerde negatif. */
	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private Kind kind;

	private Integer installmentNo;
	private Integer installmentCount;

	@Column(precision = 19, scale = 2)
	private BigDecimal purchaseTotal;

	@Column(precision = 19, scale = 2)
	private BigDecimal remainingAmount;

	private Integer remainingInstallments;

	@Column(precision = 19, scale = 2)
	private BigDecimal foreignAmount;

	@Column(length = 3)
	private String foreignCurrency;

	@ManyToOne
	private SpendingCategory category;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 8)
	private CategorySource categorySource = CategorySource.NONE;

	@ManyToOne
	private CategoryRule rule;
}
