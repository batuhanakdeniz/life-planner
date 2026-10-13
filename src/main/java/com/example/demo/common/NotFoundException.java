package com.example.demo.common;

public class NotFoundException extends RuntimeException {

	public NotFoundException(String entity, Long id) {
		super(entity + " bulunamadı: " + id);
	}
}
