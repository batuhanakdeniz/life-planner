package com.example.demo.common.vault;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VaultSyncStateRepository extends JpaRepository<VaultSyncState, String> {
}
