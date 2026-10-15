package com.example.demo.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	/**
	 * React geliştirme sunucusunun (Vite) API'ye erişebilmesi için. Uygulama yerel çalıştığından yalnızca bu
	 * makinedeki (localhost) adreslere, port fark etmeksizin izin verilir.
	 */
	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOriginPatterns("http://localhost:[*]", "http://127.0.0.1:[*]")
				.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE");
	}
}
