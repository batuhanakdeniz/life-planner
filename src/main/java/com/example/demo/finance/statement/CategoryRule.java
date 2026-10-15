package com.example.demo.finance.statement;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * "Açıklamasında X geçen işlemler → Y sınıfı" kuralı. Desen sadeleştirilmiş (büyük harf, ASCII) tutulur.
 * Kullanıcının kuralları hazır kurallardan önce gelir; birden çok kural uyarsa en uzun desen kazanır.
 */
@Entity
@Table(name = "category_rules", uniqueConstraints = @UniqueConstraint(columnNames = { "pattern", "user_defined" }))
@Getter
@Setter
@NoArgsConstructor
public class CategoryRule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 128)
	private String pattern;

	@ManyToOne(optional = false)
	private SpendingCategory category;

	/** Kullanıcının düzeltmesinden doğan kural (true) ya da hazır kural (false). */
	@Column(name = "user_defined", nullable = false)
	private boolean userDefined;

	@Column(nullable = false, updatable = false)
	private Instant createdAt = Instant.now();
}
