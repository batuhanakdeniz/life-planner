package com.example.demo.finance;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Güncel kur: 1 birim döviz kaç TL. */
@Entity
@Table(name = "exchange_rates")
@Getter
@Setter
@NoArgsConstructor
public class ExchangeRate {

	@Id
	@Enumerated(EnumType.STRING)
	@Column(length = 3)
	private Currency currency;

	@Column(nullable = false, precision = 16, scale = 6)
	private BigDecimal rateTry;

	private Instant updatedAt = Instant.now();

	public ExchangeRate(Currency currency, BigDecimal rateTry) {
		this.currency = currency;
		this.rateTry = rateTry;
	}
}
