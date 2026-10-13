package com.example.demo.bulletin;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Kanallar.md senkronunun son durumu: hangi tarafın değiştiğini anlamak için son senkrondaki
 * veritabanı ve dosya özetleri (hash) saklanır. Tek satırlık tablo.
 */
@Entity
@Table(name = "bulletin_channel_sync")
@Getter
@Setter
@NoArgsConstructor
public class ChannelSyncState {

	@Id
	private Long id = 1L;

	@Column(length = 64)
	private String dbHash;

	@Column(length = 64)
	private String fileHash;

	private Instant syncedAt;
}
