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
| **Finans · Ödemeler** | Kira, fatura, abonelik; "ödendi" + geri al, aylık sabit yük, Bugün ekranı ve bildirimlerde gösterim | `/api/finance/payments`, `/api/finance/payments/{id}/pay` |

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
- **Anlık fiyatlar için hazırlık.** Fiyatlar `PUT /api/finance/assets/{id}/price` ve `PUT /api/finance/rates/{currency}` ile güncellenir; otomatik kaynak da bu uçları kullanacak. ≈ işaretli işlemlerin kuru da o zaman tarihine göre düzeltilebilir.

## Notlar

- Şema şu an `ddl-auto=update` ile entity'lerden üretiliyor; yayına çıkmadan önce Flyway migration'larına geçilecek.
- Spring AI (Claude) bağımlılığı `pom.xml`'de yorum satırında; yapay zeka özellikleri eklenince açılacak.
- Şu an tek kullanıcılı; kimlik doğrulama yok.
