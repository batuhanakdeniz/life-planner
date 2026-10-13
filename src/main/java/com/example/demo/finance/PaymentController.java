package com.example.demo.finance;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.example.demo.finance.PaymentDtos.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/finance/payments")
@RequiredArgsConstructor
public class PaymentController {

	private final PaymentService service;

	@GetMapping
	public PaymentsOverview overview() {
		return service.overview();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PaymentResponse create(@Valid @RequestBody PaymentRequest req) {
		return service.create(req);
	}

	@PutMapping("/{id}")
	public PaymentResponse update(@PathVariable Long id, @Valid @RequestBody PaymentRequest req) {
		return service.update(id, req);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		service.delete(id);
	}

	/** Mevcut vadeyi öder; gövde isteğe bağlı: {"paidDate": "...", "amount": ...} */
	@PostMapping("/{id}/pay")
	public PaymentResponse pay(@PathVariable Long id, @Valid @RequestBody(required = false) PayRequest req) {
		return service.pay(id, req);
	}

	@GetMapping("/{id}/history")
	public List<PaymentLogResponse> history(@PathVariable Long id) {
		return service.history(id);
	}

	@DeleteMapping("/logs/{logId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteLog(@PathVariable Long logId) {
		service.deleteLog(logId);
	}
}
