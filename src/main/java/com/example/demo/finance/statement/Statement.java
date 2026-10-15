package com.example.demo.finance.statement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.example.demo.common.upload.StoredFile;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Yüklenmiş bir kredi kartı hesap özeti. */
@Entity
@Table(name = "statements")
@Getter
@Setter
@NoArgsConstructor
public class Statement {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(optional = false)
	private StoredFile file;

	@Column(nullable = false, length = 64)
	private String bank;

	/** Okuyucu gerçek bir örnekle doğrulanmadı. */
	private boolean experimental;

	@Column(length = 4)
	private String cardLast4;

	/** Hesap kesim tarihi. */
	private LocalDate periodEnd;

	private LocalDate dueDate;

	/** Ekstrede yazan dönem borcu. */
	@Column(precision = 19, scale = 2)
	private BigDecimal totalDue;

	/** Ekstrede yazan kalan toplam taksit tutarı. */
	@Column(precision = 19, scale = 2)
	private BigDecimal remainingInstallmentsTotal;

	/** Okuyucunun uyarıları, satır başına bir tane. */
	@Column(columnDefinition = "text")
	private String warnings;

	@Column(nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	@OneToMany(mappedBy = "statement", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("date ASC, id ASC")
	private List<StatementTransaction> transactions = new ArrayList<>();
}
