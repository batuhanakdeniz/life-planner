package com.example.demo.finance.statement;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Harcama sınıfı (Market, Restoran & Kafe …). Kullanıcı yenilerini ekleyebilir. */
@Entity
@Table(name = "spending_categories")
@Getter
@Setter
@NoArgsConstructor
public class SpendingCategory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 64)
	private String name;

	/** Hex renk, ör. "#4ade80". */
	@Column(nullable = false, length = 7)
	private String color;

	private int position;

	public SpendingCategory(String name, String color, int position) {
		this.name = name;
		this.color = color;
		this.position = position;
	}
}
