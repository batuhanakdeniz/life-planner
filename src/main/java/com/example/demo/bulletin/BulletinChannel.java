package com.example.demo.bulletin;

import java.time.Instant;
import java.time.LocalTime;

import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bültende takip edilen YouTube kanalı. Veritabanı asıl kaynaktır; Obsidian'daki
 * Bülten/Kanallar.md ile iki yönlü senkron tutulur (bkz. ChannelVaultSync).
 */
@Entity
@Table(name = "bulletin_channels")
@Getter
@Setter
@NoArgsConstructor
public class BulletinChannel {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	/** "@kanaladi" ya da kanal bağlantısı. Kanalın benzersiz anahtarıdır. */
	@Column(nullable = false, unique = true)
	private String handle;

	@Column(nullable = false)
	@ColumnDefault("true")
	private boolean active = true;

	/** Bültenin kapandığı saat (Türkiye saati). Varsayılan 12:00. */
	@Column(nullable = false)
	@ColumnDefault("'12:00:00'")
	private LocalTime cutoffTime = LocalTime.NOON;

	/** Kesim saatinden geriye kaç saat taranacağı. Varsayılan 24. */
	@Column(nullable = false)
	@ColumnDefault("24")
	private int lookbackHours = 24;

	private int position;

	@Column(nullable = false, updatable = false)
	private Instant createdAt = Instant.now();
}
