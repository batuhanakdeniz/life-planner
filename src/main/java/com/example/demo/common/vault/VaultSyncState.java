package com.example.demo.common.vault;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Bir Obsidian tablosunun son senkron durumu (hangi tarafın değiştiğini anlamak için iki tarafın özeti). */
@Entity
@Table(name = "vault_sync_state")
@Getter
@Setter
@NoArgsConstructor
public class VaultSyncState {

	/** Senkronun adı, ör. "bulletin-channels", "projects". */
	@Id
	@Column(length = 64)
	private String name;

	@Column(length = 64)
	private String dbHash;

	@Column(length = 64)
	private String fileHash;

	private Instant syncedAt;

	public VaultSyncState(String name) {
		this.name = name;
	}
}
