package com.example.demo.common.upload;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Yüklenen bir dosyanın kaydı. Dosyanın kendisi disktedir (depo dışında), burada yalnızca bilgileri tutulur. */
@Entity
@Table(name = "stored_files", indexes = @Index(columnList = "sha256"))
@Getter
@Setter
@NoArgsConstructor
public class StoredFile {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Hangi özellik için yüklendiği, ör. "bank-statement". Aynı dosya farklı amaçlarla ayrı kaydedilebilir. */
	@Column(nullable = false, length = 64)
	private String purpose;

	@Column(nullable = false)
	private String originalName;

	@Column(length = 128)
	private String contentType;

	private long sizeBytes;

	/** İçeriğin SHA-256 özeti; aynı dosyanın ikinci kez yüklenmesini anlamak için. */
	@Column(nullable = false, length = 64)
	private String sha256;

	/** Yükleme klasörüne göre göreli yol. */
	@Column(nullable = false, length = 500)
	private String storagePath;

	@Column(nullable = false, updatable = false)
	private Instant uploadedAt = Instant.now();
}
