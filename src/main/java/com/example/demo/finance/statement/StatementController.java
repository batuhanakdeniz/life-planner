package com.example.demo.finance.statement;

import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.finance.statement.StatementDtos.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/finance")
public class StatementController {

	private final StatementService service;

	public StatementController(StatementService service) {
		this.service = service;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void seed() {
		service.seedDefaultsIfEmpty();
	}

	// --- Hesap özetleri ---
	/** PDF hesap özeti yükler, okur ve sınıflandırır. Aynı dosya tekrar yüklenirse mevcut kayıt döner. */
	@PostMapping(path = "/statements", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public StatementSummary upload(@RequestParam("file") MultipartFile file) {
		return service.upload(file);
	}

	@GetMapping("/statements")
	public List<StatementSummary> list() {
		return service.list();
	}

	@GetMapping("/statements/{id}")
	public StatementDetail detail(@PathVariable Long id) {
		return service.detail(id);
	}

	@DeleteMapping("/statements/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		service.delete(id);
	}

	@PutMapping("/statements/transactions/{id}/category")
	public TransactionView setCategory(@PathVariable Long id, @Valid @RequestBody SetCategoryRequest req) {
		return service.setCategory(id, req);
	}

	// --- Sınıflar ve kurallar ---
	@GetMapping("/spending-categories")
	public List<CategoryView> categories() {
		return service.categories();
	}

	@PostMapping("/spending-categories")
	@ResponseStatus(HttpStatus.CREATED)
	public CategoryView createCategory(@Valid @RequestBody CategoryRequest req) {
		return service.createCategory(req);
	}

	@PutMapping("/spending-categories/{id}")
	public CategoryView updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest req) {
		return service.updateCategory(id, req);
	}

	/** Kullanıcının düzeltmelerinden oluşan kurallar. */
	@GetMapping("/category-rules")
	public List<RuleView> rules() {
		return service.userRules();
	}

	@PutMapping("/category-rules/{id}")
	public RuleView updateRule(@PathVariable Long id, @Valid @RequestBody RuleRequest req) {
		return service.updateRule(id, req);
	}

	@DeleteMapping("/category-rules/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteRule(@PathVariable Long id) {
		service.deleteRule(id);
	}
}
