# Hayat Planlayıcı — durum ve kararlar

Yeni bir oturumda bu dosyayı önce oku. Ayrıntılı kurulum ve çalıştırma için `README.md`'ye bak.

## Mimari

- **Backend:** Spring Boot 4, Java 25, PostgreSQL (`lifeplanner`), port 8080. Şema `ddl-auto=update` ile üretiliyor; yayın öncesi Flyway'e geçilecek.
- **Web:** React + Vite, port 5173. `/api` istekleri backend'e proxy ediliyor (`API_TARGET` ile değiştirilebilir).
- **Masaüstü:** JavaFX, `desktop/` klasörü. Aynı API'yi kullanır.
- **Obsidian kasası:** `~/Desktop/vault/batu/LifePlanner` (`LIFEPLANNER_VAULT`). Bülten ve Projeler tabloları iki yönlü senkron (`common/vault/VaultTableSync`).
- **Yüklenen dosyalar:** depo dışında `~/.lifeplanner/uploads` (`LIFEPLANNER_UPLOADS`). Ortak altyapı: `common/upload`, web'de `FileDrop`, masaüstünde `FileDropZone`.
- **Testler:** `./mvnw test` (38 test). Python tarafı: `python3 -m unittest tools/bulletin/test_bulletin.py`.

## Modüller

Bugün, Görevler, Alışkanlıklar, Hedefler, Günlük, Finans (Portföy / İşlemler / Ödemeler), Bülten, Projeler.

Finans'ta Ödemeler sekmesi iki alt sekmeye sahip: **Düzenli** ödemeler ve **Hesap özetleri** (kredi kartı ekstreleri).

## Kararlar

**Genel**
- Kullanıcı: Batuhan Akdeniz. Tek kullanıcılı, kimlik doğrulama yok.
- Depo herkese açık (`batuhanakdeniz/life-planner`). Commit'lerde gizli GitHub e-postası kullanılıyor (`49098686+batuhanakdeniz@users.noreply.github.com`).
- Kişisel veri (ekstre, kart bilgisi, adres) depoya girmez. Testler uydurma satırlarla yazılır.
- Yerel PostgreSQL şifresiz; şifre gerekirse macOS Anahtarlığı önerildi.
- Tema: koyu/neon, mor-camgöbeği vurgular. Açık tema da var.
- Logo: neon mor ajanda (`assets/logo`). Dock, menü çubuğu ve favicon bu logoyu kullanıyor.

**Finans**
- Portföy ortalama maliyet yöntemiyle, her işlem kendi gününün USD/TRY kuruyla hem TL hem $ olarak tutuluyor.
- Fiyat kaynakları: TCMB (kur), Truncgil (altın/gümüş, alış fiyatı), Yahoo (BIST ve ABD hisseleri). 15 dk'da bir (piyasa saatinde), diğer zamanlarda 3 saatte bir.
- Altın türleri: Gram, Çeyrek (`ceyrek-altin`) ve Ata lira (`ata-altin`) hazır varlık olarak ekli; Cumhuriyet, Yarım, Tam ve gümüş için de Truncgil eşleşmesi var.
- Kursuz işlemlerde işlem gününün TCMB kuru otomatik kullanılıyor.
- Sayı girişi Türkçe: `6.250,50`.
- Hesap özetleri: Yapı Kredi (World) gerçek ekstreyle doğrulandı. İş Bankası (Maximum) **deneysel**, ilk gerçek ekstreyle kontrol edilmeli.
- Sınıflandırma: anahtar kelime kuralları (`CategoryDefaults`). Kullanıcı kuralları önce gelir, en uzun eşleşme kazanır. Sınıf değiştirilip "bundan sonra" denirse kural kaydedilir.

**Bülten**
- Her gün 12:05'te zamanlanmış Claude Code görevi (`gunluk-bulten`) çalışır. Talimat: `tools/bulletin/GOREV.md`.
- Varsayılan tarama aralığı: dünkü 12:00 → bugünkü 12:00 (Türkiye saati), kanal başına değiştirilebilir.
- Altyazı yoksa başlık ve açıklamadan özet çıkarılır. Özetler Türkçe.
- Kanallar henüz verilmedi (Obsidian'da `Bülten/Kanallar.md` ya da `bulletin_channels` tablosu).

**Projeler**
- Metrikler şimdilik sahte (`MockMetricsSource`). Gerçek kaynaklar `ProjectMetricsSource` arayüzüyle eklenecek.
- Sağlık eşikleri: hata oranı > %1, uptime < %99,5 ya da 10+ açık hata → Uyarı; uptime < %98 → Kesinti.

**Çalışma kuralları (bellekte de kayıtlı)**
- Her tamamlanmış değişiklik commit'lenip `main`'e push edilir.
- Yeni sayfa ya da özellik kararlarında önce soru sorulur, seçenekler sunulur.

## Açık işler

1. Hesap özetlerinde 21 sınıflandırılamayan iş yeri var (toplam ~29.500 TL): PAYCELL/AVEK OTOMOTİ, MONEYPAY *DIJITAL PLAT, HESA GIDA, MAHBEE, ES PERFORMANS, ZULA EMAAR, SHS İST METROPOL, DIGITAL PLATFORM, L J MAĞAZACILIK, IYZICO/GUZEL.NET, SUNCORP GIDA, TURKIYE OTOMOBIL SPOR, PASCO GIDA, FIUM TEKNOLOJİ, THE ONE, KEREM ELDEN, KENT MARKET, KZL GIDA, STAD DURUM, MARİN TOBACCO, EGEA OTOMAT. Kullanıcının sınıf bilgisi bekleniyor.
2. Masaüstü hesap özetlerinin gerçek tıklamayla denenmesi (yalnızca ekran görüntüsüyle doğrulandı).
3. İş Bankası ekstresinin ilk gerçek örneğiyle okuyucunun düzeltilmesi.
4. Bülten için 5 takip kanalının kullanıcıdan alınması.
5. Projeler: gerçek metrik kaynaklarının seçilmesi.
6. Yayın öncesi: şemanın Flyway'e taşınması.

## Sıradaki adım

Açık işlerden birini seçip kullanıcıya sor. Özellikle 1 numaralı iş (sınıf bilgisi) hemen kullanılabilir.
