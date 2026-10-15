package com.example.demo.project;

import java.time.Instant;

import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Takip edilen bir proje. Obsidian'daki Projeler/Projeler.md ile iki yönlü senkron tutulur. */
@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
public class Project {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Kısa, benzersiz kod (ör. "kampus-pazari"). Obsidian tablosundaki satırın anahtarı. */
	@Column(nullable = false, unique = true, length = 64)
	private String slug;

	@Column(nullable = false)
	private String name;

	@Column(length = 500)
	private String description;

	/** Ör. "web", "mobil", "web + mobil". */
	@Column(length = 64)
	private String platform;

	/** Projenin kendi dashboard'unun adresi. */
	@Column(length = 500)
	private String dashboardUrl;

	@Column(nullable = false)
	@ColumnDefault("true")
	private boolean active = true;

	/** Metriklerin kaynağı. Şimdilik yalnızca "mock"; gerçek kaynaklar eklendikçe genişleyecek. */
	@Column(nullable = false, length = 32)
	@ColumnDefault("'mock'")
	private String metricsSource = "mock";

	private int position;

	@Column(nullable = false, updatable = false)
	private Instant createdAt = Instant.now();
}
