package com.example.demo.common.upload;

import java.time.Instant;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Yüklenmiş dosyaları geri indirme. Yükleme, işlemi yapan özelliğin kendi uç noktasından yapılır
 * (ör. /api/finance/statements) ve o özellik {@link UploadService}'i kullanır.
 */
@RestController
@RequestMapping("/api/uploads")
public class UploadController {

	public record FileInfo(Long id, String purpose, String originalName, long sizeBytes, Instant uploadedAt) {

		public static FileInfo from(StoredFile f) {
			return new FileInfo(f.getId(), f.getPurpose(), f.getOriginalName(), f.getSizeBytes(), f.getUploadedAt());
		}
	}

	private final UploadService uploads;

	public UploadController(UploadService uploads) {
		this.uploads = uploads;
	}

	@GetMapping("/{id}")
	public FileInfo info(@PathVariable Long id) {
		return FileInfo.from(uploads.get(id));
	}

	@GetMapping("/{id}/content")
	public ResponseEntity<byte[]> content(@PathVariable Long id) {
		var f = uploads.get(id);
		var type = f.getContentType() != null ? MediaType.parseMediaType(f.getContentType()) : MediaType.APPLICATION_OCTET_STREAM;
		return ResponseEntity.ok()
				.contentType(type)
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(f.getOriginalName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
				.body(uploads.read(f));
	}
}
