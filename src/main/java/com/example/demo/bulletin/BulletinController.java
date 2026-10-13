package com.example.demo.bulletin;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.demo.bulletin.BulletinDtos.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/bulletins")
public class BulletinController {

	private final BulletinService service;
	private final ChannelVaultSync sync;

	public BulletinController(BulletinService service, ChannelVaultSync sync) {
		this.service = service;
		this.sync = sync;
	}

	/** GET /api/bulletins?date=2026-10-14 (tarih yoksa en son bülten) */
	@GetMapping
	public ResponseEntity<BulletinResponse> get(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return ResponseEntity.of(service.find(date));
	}

	@GetMapping("/dates")
	public List<LocalDate> dates() {
		return service.dates();
	}

	/** Zamanlanmış görev için: hazırlanması gereken günler (telafi dahil). */
	@GetMapping("/pending")
	public List<LocalDate> pending() {
		return service.pendingDates(ZonedDateTime.now());
	}

	/** Zamanlanmış görev için: o gün hangi kanal hangi aralıkta taranacak. Önce Obsidian ile senkron yapılır. */
	@GetMapping("/plan")
	public RunPlan plan(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		sync.sync();
		return service.plan(date);
	}

	@PutMapping("/{date}")
	public BulletinResponse save(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@Valid @RequestBody BulletinPayload payload) {
		return service.save(date, payload);
	}

	// --- Kanallar ---
	@GetMapping("/channels")
	public List<ChannelResponse> channels() {
		return service.listChannels();
	}

	@PostMapping("/channels")
	@ResponseStatus(HttpStatus.CREATED)
	public ChannelResponse create(@Valid @RequestBody ChannelRequest req) {
		return service.createChannel(req);
	}

	@PutMapping("/channels/{id}")
	public ChannelResponse update(@PathVariable Long id, @Valid @RequestBody ChannelRequest req) {
		return service.updateChannel(id, req);
	}

	@DeleteMapping("/channels/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		service.deleteChannel(id);
	}

	/** Kanallar.md ile hemen senkron yap. */
	@PostMapping("/channels/sync")
	public ChannelVaultSync.Result syncNow() {
		return sync.sync();
	}
}
