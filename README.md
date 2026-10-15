# 🌱 Hayat Planlayıcı

Günlük görevler, alışkanlıklar, uzun vadeli hedefler ve günlük notlar için kişisel planlayıcı.

```
demo/
├── src/       → Spring Boot backend (REST API, PostgreSQL)      :8080
├── web/       → React + Vite web sitesi                         :5173
└── desktop/   → JavaFX masaüstü uygulaması (tam + mini mod)
```

## Gereksinimler

- JDK 25 → `brew install openjdk@25`
- PostgreSQL 17 → `brew install postgresql@17 && brew services start postgresql@17 && createdb lifeplanner`
- Node.js → `brew install node`

JDK 25 Homebrew'de "keg-only" kurulur. Her terminalde ayrıca vermemek için `~/.zshrc` dosyasına ekle:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

## Çalıştırma

Her biri ayrı bir terminalde çalıştırılır:

```bash
./mvnw spring-boot:run                          # 1) backend
npm --prefix web install && npm --prefix web run dev   # 2) web → http://localhost:5173
./mvnw -f desktop/pom.xml javafx:run            # 3) masaüstü
```

Veritabanı bağlantısı ortam değişkenleriyle değiştirilebilir: `DB_URL`, `DB_USER`, `DB_PASSWORD`.
Masaüstü uygulamasının API adresi için `LIFEPLANNER_API` kullanılır (varsayılan `http://localhost:8080/api`).

## Modüller

| Modül | Web / Masaüstü | API |
|---|---|---|
| **Bugün** | Özet kartları, günün görevleri, alışkanlıklar, hedef ilerlemesi, günün notu | `GET /api/dashboard?date=` |
| **Görevler** | Haftalık sütunlar · Gruplu liste (gecikmiş/bugün/yarın/…) · Kanban (sürükle-bırak) | `/api/tasks`, `/api/tasks/board`, `PATCH /api/tasks/{id}/status` |
| **Alışkanlıklar** | Haftalık tablo, seri (streak), 6 aylık ısı haritası, belirli günler | `/api/habits`, `/api/habits/{id}/check`, `/api/habits/{id}/history` |
| **Hedefler** | Zaman çizelgesi, kategori, adımlar (kilometre taşları) ve ilerleme % | `/api/goals`, `/api/goals/{id}/milestones` |
| **Günlük** | Ruh haline göre renklenen takvim + editör | `/api/journal`, `PUT /api/journal/{date}` |
| **Finans · Portföy** | Toplam değer ₺/$, gerçekleşen/gerçekleşmemiş kâr-zarar, halka grafikte dağılım, varlık tablosu, kur ve fiyat girişi | `/api/finance/portfolio`, `/api/finance/assets`, `/api/finance/rates/{USD\|EUR}` |
| **Finans · İşlemler** | Döviz, altın, hisse/fon alım-satımları; işlem günündeki USD/TRY kuru (bilinmiyorsa ≈ güncel kur) | `/api/finance/trades` |
| **Bülten** | Takip edilen YouTube kanallarının günlük özeti: üstte "Günün özeti", altında katlanır kanal kartları; videoda önemli noktalar, zaman damgalı bölümler, kişi/şirket/rakamlar | `/api/bulletins`, `/api/bulletins/channels` |
| **Projeler** | Projelerin özet metrikleri: toplam aktif kullanıcı, MAU, MRR, sağlık; proje kartlarında 30 günlük grafik ve sağlık sebebi; tıklanınca detay paneli | `/api/projects`, `/api/projects/{kod}`, `/api/projects/catalog` |
| **Finans · Ödemeler** | Kira, fatura, abonelik; "ödendi" + geri al, aylık sabit yük, Bugün ekranı ve bildirimlerde gösterim | `/api/finance/payments`, `/api/finance/payments/{id}/pay` |
| **Finans · Hesap özetleri** | Kredi kartı ekstresi (PDF) yükleme; işlemler sınıflara ayrılır, harcama dağılımı, taksitler ve kalan borç; sınıf değiştirilip kural olarak hatırlanır | `/api/finance/statements`, `/api/finance/spending-categories`, `/api/finance/category-rules` |

### Masaüstüne özel

- **Mini mod**: küçük "Bugün" penceresi, hızlı görev ekleme
- **📌 Her zaman üstte**: tercih kalıcı olarak saklanır
- **Menü çubuğu ikonu**: pencere kapatılınca uygulama menü çubuğunda çalışmaya devam eder
- **Hatırlatıcılar**: belirlenen saatlerde (varsayılan 09:00, 21:00) kalan işler için macOS bildirimi

### Finans hesaplama kuralları

- **Ortalama maliyet yöntemi.** Satışta, satılan miktarın ortalama maliyeti düşülür; aradaki fark gerçekleşen kâr/zarardır.
- **Çift para birimi.** Her işlem kendi günündeki USD/TRY kuruyla hem TL hem $ maliyet olarak tutulur. Böylece "TL'de kazandım ama dolar bazında kazanmadım" durumu görünür.
- **Güncel değer.** Elle girilen fiyat kullanılır, o yoksa son işlem fiyatı ("son işlem fiyatı" etiketiyle). USD/EUR varlıkları doğrudan kurdan fiyatlanır.
- **Sayı girişi Türkçedir.** `6.250,50` = altı bin iki yüz elli virgül elli. Nokta binlik ayırıcıdır.
- **İşlem kuru.** Boş bırakılırsa işlem tarihinin TCMB kuru otomatik kullanılır. TCMB'ye ulaşılamazsa güncel kur kullanılır ve işlem ≈ ile işaretlenir; sonraki güncellemede gerçek kurla düzeltilir.

### Otomatik fiyatlar

| Kaynak | Ne için | Not |
|---|---|---|
| [TCMB](https://www.tcmb.gov.tr/kurlar/today.xml) | USD/TRY, EUR/TRY (döviz alış) ve geçmiş kurlar | Resmî; iş günleri ~15:30'da yayınlanır |
| [Truncgil Finans](https://finans.truncgil.com/today.json) | Gram, çeyrek, yarım, tam altın, gümüş, ons (alış) | Gayriresmî ücretsiz kaynak |
| Yahoo Finance | BIST (`THYAO.IS`) ve ABD hisseleri | Resmî olmayan uç; BIST ~15 dk gecikmeli |

- Varlık eklenince kaynak otomatik seçilir (altın → Truncgil, hisse → Yahoo, USD/EUR → TCMB, fon → elle). Portföy tablosundaki kaynak etiketine tıklayıp değiştirilebilir.
- Hafta içi 09:00–23:45 arası 15 dakikada bir, diğer zamanlarda 3 saatte bir güncellenir; Portföy'deki **↻ Güncelle** ile anında.
- Bir kaynak çalışmazsa son bilinen fiyat kalır ve etiket ⚠ ile işaretlenir. 26 saatten eski fiyatlar sarı görünür.
- Kapatmak için: `lifeplanner.prices.enabled=false`.

### Bülten nasıl çalışır

1. Her gün **12:05**'te zamanlanmış bir Claude Code görevi çalışır (talimatı: [`tools/bulletin/GOREV.md`](tools/bulletin/GOREV.md)). Uygulama kapalıysa açıldığında çalışır; kaçırılan günler (en fazla 7) sırayla telafi edilir.
2. [`tools/bulletin/bulletin.py`](tools/bulletin/bulletin.py) yt-dlp ile her kanalın zaman aralığındaki videolarını ve altyazılarını toplar (kanal başına günde en fazla 15 video).
3. Claude altyazıları Türkçe özetler ve backend'e gönderir; bülten web/masaüstünde görünür ve Obsidian'a `Bülten/YYYY-MM-DD.md` olarak yazılır.

**Kanalları düzenleme:** Obsidian'da `Bülten/Kanallar.md` tablosu ya da veritabanındaki `bulletin_channels` tablosu. İkisi iki yönlü senkron (en geç 5 dk). Varsayılan tarama aralığı her kanal için dünkü 12:00 → bugün 12:00 (Türkiye saati); kanal başına `Kesim` ve `Geriye (saat)` sütunlarıyla değiştirilebilir.

Gereken: `brew install yt-dlp`. Kasa yolu `LIFEPLANNER_VAULT` ile değiştirilebilir.

### Hesap özetleri nasıl çalışır

- **Desteklenen bankalar:** Yapı Kredi (World) gerçek bir ekstreyle doğrulandı. İş Bankası (Maximum) **deneysel**; genel "tarih açıklama tutar" kalıbıyla okunur. Bilinmeyen bankalar da aynı kalıpla denenir.
- **Okuma kontrolü:** okunan işlemlerin toplamı ekstredeki dönem borcuyla karşılaştırılır; tutmazsa uyarı gösterilir.
- **Sınıflandırma:** açıklamada geçen anahtar kelimelere göre (ör. `MIGROS` → Market). Bir işlemin sınıfını değiştirip "bundan sonra" dersen kural kaydedilir ve tüm ekstrelerde uygulanır. Senin kuralların hazır kurallardan önce gelir; birden çok kural uyarsa en uzun eşleşme kazanır.
- **Dosyalar** depo dışında, `~/.lifeplanner/uploads` altında tutulur (`LIFEPLANNER_UPLOADS` ile değiştirilebilir). Aynı dosya iki kez yüklenirse mevcut kayıt açılır.
- **Ortak yükleme altyapısı:** `common/upload` (`UploadService`, `DocumentText`) ve web'de `FileDrop` bileşeni başka özellikler için de kullanılabilir.
- Yeni bir banka eklemek için `StatementParser` arayüzünün yeni bir uygulaması yazılır.

### Projeler nasıl çalışır

- Proje listesi (ad, kod, açıklama, platform, dashboard bağlantısı) Obsidian'daki `Projeler/Projeler.md` ile iki yönlü senkron. İlk açılışta 3 örnek proje eklenir.
- **Metrikler şimdilik örnek (sahte) veridir** ve sayfada "örnek veri" etiketiyle gösterilir. Değerler proje kodu ve güne göre üretilir; aynı gün içinde değişmez.
- Gerçek veriye geçmek için `ProjectMetricsSource` arayüzünün yeni bir uygulaması yazılır (ör. analitik aracı, ödeme sağlayıcısı, uptime servisi) ve projenin `metrics_source` alanı ona çevrilir.
- Sağlık eşikleri: hata oranı > %1, 30 günlük uptime < %99,5 ya da 10+ açık hata → **Uyarı**; uptime < %98 → **Kesinti**.

## Notlar

- Şema şu an `ddl-auto=update` ile entity'lerden üretiliyor; yayına çıkmadan önce Flyway migration'larına geçilecek.
- Spring AI (Claude) bağımlılığı `pom.xml`'de yorum satırında; yapay zeka özellikleri eklenince açılacak.
- Şu an tek kullanıcılı; kimlik doğrulama yok.
