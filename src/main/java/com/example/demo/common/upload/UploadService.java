package com.example.demo.common.upload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.common.NotFoundException;

/**
 * Yüklenen dosyaları diske kaydeden ortak servis. Herhangi bir özellik (hesap özeti, fatura, belge vb.)
 * kendi "purpose" değeriyle kullanır. Dosyalar depo dışında, {@code lifeplanner.uploads.path} altında
 * yıl/ay klasörlerinde rastgele adlarla tutulur.
 */
@Service
@Transactional
public class UploadService {

	/** Yükleme sonucu; duplicate, aynı içeriğin bu amaçla daha önce yüklendiğini belirtir. */
	public record Upload(StoredFile file, boolean duplicate) {
	}

	private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "csv", "xlsx", "xls", "png", "jpg", "jpeg", "txt");

	private final StoredFileRepository files;
	private final Path root;

	public UploadService(StoredFileRepository files, @Value("${lifeplanner.uploads.path}") String root) {
		this.files = files;
		this.root = Path.of(root);
	}

	public Upload store(MultipartFile file, String purpose) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("Dosya boş");
		}
		try {
			return store(file.getBytes(), file.getOriginalFilename(), file.getContentType(), purpose);
		}
		catch (IOException e) {
			throw new IllegalStateException("Dosya okunamadı: " + e.getMessage(), e);
		}
	}

	public Upload store(byte[] content, String originalName, String contentType, String purpose) {
		var name = originalName == null || originalName.isBlank() ? "dosya" : Path.of(originalName).getFileName().toString();
		var ext = extension(name);
		if (!ALLOWED_EXTENSIONS.contains(ext)) {
			throw new IllegalArgumentException("Desteklenmeyen dosya türü: ." + ext);
		}
		var sha = sha256(content);
		var existing = files.findFirstByPurposeAndSha256(purpose, sha);
		if (existing.isPresent() && Files.exists(resolve(existing.get()))) {
			return new Upload(existing.get(), true);
		}
		var today = LocalDate.now();
		var relative = Path.of(String.valueOf(today.getYear()), String.format("%02d", today.getMonthValue()), UUID.randomUUID() + "." + ext);
		try {
			var target = root.resolve(relative);
			Files.createDirectories(target.getParent());
			Files.write(target, content);
		}
		catch (IOException e) {
			throw new IllegalStateException("Dosya kaydedilemedi: " + e.getMessage(), e);
		}
		var f = new StoredFile();
		f.setPurpose(purpose);
		f.setOriginalName(name);
		f.setContentType(contentType);
		f.setSizeBytes(content.length);
		f.setSha256(sha);
		f.setStoragePath(relative.toString());
		return new Upload(files.save(f), false);
	}

	@Transactional(readOnly = true)
	public StoredFile get(Long id) {
		return files.findById(id).orElseThrow(() -> new NotFoundException("Dosya", id));
	}

	@Transactional(readOnly = true)
	public byte[] read(StoredFile f) {
		try {
			return Files.readAllBytes(resolve(f));
		}
		catch (IOException e) {
			throw new IllegalStateException("Dosya okunamadı: " + f.getOriginalName(), e);
		}
	}

	/** Kaydı ve diskteki dosyayı siler. */
	public void delete(Long id) {
		var f = get(id);
		try {
			Files.deleteIfExists(resolve(f));
		}
		catch (IOException e) {
			throw new IllegalStateException("Dosya silinemedi: " + e.getMessage(), e);
		}
		files.delete(f);
	}

	private Path resolve(StoredFile f) {
		var p = root.resolve(f.getStoragePath()).normalize();
		if (!p.startsWith(root.normalize())) {
			throw new IllegalStateException("Geçersiz dosya yolu");
		}
		return p;
	}

	static String extension(String name) {
		int dot = name.lastIndexOf('.');
		return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	static String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		}
		catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
