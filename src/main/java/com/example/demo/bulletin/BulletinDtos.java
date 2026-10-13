package com.example.demo.bulletin;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class BulletinDtos {

	private BulletinDtos() {
	}

	// ---------- Kanallar ----------
	public record ChannelRequest(
			@NotBlank @Size(max = 255) String name,
			@NotBlank @Size(max = 255) String handle,
			Boolean active,
			LocalTime cutoffTime,
			@Min(1) @Max(168) Integer lookbackHours) {
	}

	public record ChannelResponse(Long id, String name, String handle, String url, boolean active,
			LocalTime cutoffTime, int lookbackHours) {
	}

	/** Bir bülten günü için bir kanalın taranacağı aralık. */
	public record ChannelWindow(Long channelId, String name, String handle, String url, Instant windowStart, Instant windowEnd) {
	}

	public record RunPlan(LocalDate date, List<ChannelWindow> channels) {
	}

	// ---------- Bülten ----------
	public record Chapter(@NotNull @Min(0) Integer seconds, @NotBlank String label) {
	}

	public record Mentions(List<String> people, List<String> companies, List<String> numbers) {
	}

	public record VideoPayload(
			@NotBlank @Size(max = 32) String videoId,
			@NotBlank @Size(max = 500) String title,
			Instant publishedAt,
			Integer durationSeconds,
			boolean hasTranscript,
			String summary,
			List<String> keyPoints,
			List<@Valid Chapter> chapters,
			Mentions mentions) {
	}

	public record ChannelPayload(
			@NotNull Long channelId,
			String shortSummary,
			@Size(max = 500) String error,
			@NotNull Instant windowStart,
			@NotNull Instant windowEnd,
			/** Sınır yüzünden özetlenmeyen yaklaşık video sayısı. */
			Integer skippedVideos,
			List<@Valid VideoPayload> videos) {
	}

	/** Zamanlanmış görevin gönderdiği tam bülten. Aynı gün tekrar gönderilirse üzerine yazılır. */
	public record BulletinPayload(String overview, @NotNull List<@Valid ChannelPayload> channels) {
	}

	public record VideoResponse(String videoId, String title, String url, Instant publishedAt, Integer durationSeconds,
			boolean hasTranscript, String summary, List<String> keyPoints, List<Chapter> chapters, Mentions mentions) {
	}

	public record ChannelDigestResponse(Long channelId, String channelName, String channelHandle, String shortSummary,
			String error, Instant windowStart, Instant windowEnd, int skippedVideos, List<VideoResponse> videos) {
	}

	public record BulletinResponse(LocalDate date, String overview, Instant generatedAt, List<ChannelDigestResponse> channels) {
	}
}
