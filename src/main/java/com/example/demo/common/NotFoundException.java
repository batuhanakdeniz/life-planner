package com.example.demo.common;

public class NotFoundException extends RuntimeException {

	public NotFoundException(String entity, Long id) {
		super(entity + " bulunamadı: " + id);
	}

	public NotFoundException(String entity, String key) {
		super(entity + " bulunamadı: " + key);
	}
}
