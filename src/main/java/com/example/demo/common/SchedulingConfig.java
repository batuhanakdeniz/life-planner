package com.example.demo.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** @Scheduled görevleri (fiyat güncelleme, Obsidian senkronu) etkinleştirir. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
