package com.example.demo.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Bir alım veya satım işlemi. */
@Entity
@Table(name = "trades", indexes = @Index(columnList = "date"))
@Getter
@Setter
@NoArgsConstructor
public class Trade {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	private Asset asset;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 4)
	private TradeSide side;

	@Column(nullable = false)
	private LocalDate date;

	@Column(nullable = false, precision = 24, scale = 8)
	private BigDecimal quantity;

	/** Birim fiyat, varlığın fiyat para biriminde. */
	@Column(nullable = false, precision = 24, scale = 8)
	private BigDecimal unitPrice;

	/** İşlem günündeki USD/TRY kuru; maliyetin hem TL hem $ olarak doğru hesaplanması için. */
	@Column(nullable = false, precision = 16, scale = 6)
	private BigDecimal usdTryRate;

	/**
	 * Kur kullanıcı tarafından girilmedi; kayıt anındaki güncel kur kullanıldı.
	 * Otomatik kur kaynağı eklendiğinde bu işlemlerin kuru işlem tarihine göre düzeltilecek.
	 */
	@Column(nullable = false)
	@ColumnDefault("false")
	private boolean rateEstimated;

	/** Komisyon vb. masraf, varlığın fiyat para biriminde. */
	@Column(precision = 24, scale = 8)
	private BigDecimal fee;

	@Column(length = 500)
	private String note;
}
