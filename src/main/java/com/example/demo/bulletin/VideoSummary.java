package com.example.demo.bulletin;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tek bir videonun özeti. Liste alanları JSON metni olarak saklanır. */
@Entity
@Table(name = "bulletin_videos")
@Getter
@Setter
@NoArgsConstructor
public class VideoSummary {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private ChannelDigest digest;

	@Column(nullable = false, length = 32)
	private String videoId;

	@Column(nullable = false, length = 500)
	private String title;

	private Instant publishedAt;

	private Integer durationSeconds;

	/** Altyazı bulunamadıysa özet başlık ve açıklamadan çıkarılmıştır. */
	private boolean hasTranscript;

	@Column(columnDefinition = "text")
	private String summary;

	/** JSON: ["madde", ...] */
	@Column(columnDefinition = "text")
	private String keyPointsJson;

	/** JSON: [{"seconds": 252, "label": "..."}, ...] */
	@Column(columnDefinition = "text")
	private String chaptersJson;

	/** JSON: {"people": [...], "companies": [...], "numbers": [...]} */
	@Column(columnDefinition = "text")
	private String mentionsJson;
}
