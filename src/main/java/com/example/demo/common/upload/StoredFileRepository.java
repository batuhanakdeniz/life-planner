package com.example.demo.common.upload;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

	Optional<StoredFile> findFirstByPurposeAndSha256(String purpose, String sha256);
}
