package com.example.demo.finance;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Alınıp satılan bir yatırım aracı: ör. Gram altın (TRY), USD (TRY), AAPL (USD). */
@Entity
@Table(name = "assets")
@Getter
@Setter
@NoArgsConstructor
public class Asset {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Kısa kod; ileride anlık fiyat kaynağıyla eşleştirmek için kullanılacak (ör. "GRAM_ALTIN", "USD", "THYAO"). */
	@Column(nullable = false, unique = true, length = 32)
	private String symbol;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private AssetType type;

	/** Fiyatın hangi para biriminde ifade edildiği. Yalnızca TRY veya USD. */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 3)
	private Currency priceCurrency = Currency.TRY;

	/** Miktar birimi, ör. "gr", "adet", "lot", "$". */
	@Column(length = 16)
	private String unit;

	/** Elle girilen (ileride otomatik güncellenecek) güncel birim fiyat. */
	@Column(precision = 24, scale = 8)
	private BigDecimal currentPrice;

	private Instant priceUpdatedAt;

	@Column(nullable = false, updatable = false)
	private Instant createdAt = Instant.now();
}
