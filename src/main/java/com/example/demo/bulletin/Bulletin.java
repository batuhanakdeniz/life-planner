package com.example.demo.bulletin;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Bir günün bülteni. */
@Entity
@Table(name = "bulletins")
@Getter
@Setter
@NoArgsConstructor
public class Bulletin {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private LocalDate date;

	/** En üstteki "Günün özeti": kanalların kısa özetlerinin birleşimi. */
	@Column(columnDefinition = "text")
	private String overview;

	private Instant generatedAt = Instant.now();

	@OneToMany(mappedBy = "bulletin", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position")
	private List<ChannelDigest> channels = new ArrayList<>();
}
