package com.example.demo.bulletin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Bir bültende bir kanalın kısa özeti ve videoları. */
@Entity
@Table(name = "bulletin_channel_digests")
@Getter
@Setter
@NoArgsConstructor
public class ChannelDigest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Bulletin bulletin;

	/** Kanal sonradan silinirse null olur; ad ve handle kopyası kalır. */
	@ManyToOne(fetch = FetchType.LAZY)
	private BulletinChannel channel;

	@Column(nullable = false)
	private String channelName;

	@Column(nullable = false)
	private String channelHandle;

	/** Kanalın bu aralıktaki kısa özeti (1-3 cümle). Video yoksa null. */
	@Column(columnDefinition = "text")
	private String shortSummary;

	/** Video listelenemediyse hata mesajı. */
	@Column(length = 500)
	private String error;

	@Column(nullable = false)
	private Instant windowStart;

	@Column(nullable = false)
	private Instant windowEnd;

	private int position;

	/** Kanal başına video sınırı yüzünden özetlenmeyen (yaklaşık) video sayısı. */
	@Column(nullable = false)
	@ColumnDefault("0")
	private int skippedVideos;

	@OneToMany(mappedBy = "digest", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("publishedAt DESC")
	private List<VideoSummary> videos = new ArrayList<>();
}
