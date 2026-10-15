package com.example.demo.finance.statement;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** İlk açılışta eklenen sınıflar ve hazır iş yeri kuralları (desenler sadeleştirilmiş: büyük harf, ASCII). */
final class CategoryDefaults {

	record Def(String name, String color, List<String> patterns) {
	}

	static final String FEES = "Banka Faiz & Ücret";

	static final List<Def> CATEGORIES = List.of(
			new Def("Market", "#4ade80", List.of("MIGROS", "A101", "BIM ", "SOK MARKET", "CARREFOUR", "GETIR", "HIPERMARKET",
					"MINI MARKET", "MACROCENTER", "TEKEL", "EKMEK", "FIRIN", "MANAV", "KASAP", "FILE FILE")),
			new Def("Restoran & Kafe", "#fb923c", List.of("UBER EATS", "YEMEKSEPETI", "TRENDYOL YEMEK", "STARBUCKS", "CAFE", "KAFE",
					"COFFEE", "KAHVE", "RESTAURANT", "RESTORAN", "LOKANTA", "DONER", "BALIK", "KUNEFE", "KATMER", "MUTFAG", "SALAD",
					"GURME", "ASPAVA", "BURGER", "PIZZA", "KEBAP", "KANTIN", "HAPPY MOONS", "KIZILKAYALAR")),
			new Def("Ulaşım & Akaryakıt", "#38bdf8", List.of("SHELL", "OPET", "PETROL OFISI", "AYTEMIZ", "TOTALENERGIES", "HGS ",
					"OGS ", "OTOPARK", "BITAKSI", "TAKSI", "MARTI", "ISTANBULKART", "BELBIM", "UBER")),
			new Def("Giyim", "#f472b6", List.of("MANGO", "MUDO", "BOYNER", "ZARA", "LC WAIKIKI", "DEFACTO", "KOTON", "H&M", "BERSHKA",
					"PULL&BEAR", "MAVI ", "NETWORK", "VAKKO", "BEYMEN")),
			new Def("Elektronik", "#a78bfa", List.of("MEDIA MARKT", "MEDIAMARKT", "TEKNOSA", "VATAN BILGISAYAR", "LG.COM", "SAMSUNG")),
			new Def("Sağlık", "#f87171", List.of("ECZANE", "HASTANE", "KLINIK", "DIS HEKIM", "DENTAL", "OPTIK", "LABORATUVAR")),
			new Def("Eğitim", "#facc15", List.of("UDEMY", "COURSERA", "KITAP", "UNIVERSITE", "KURS ")),
			new Def("Eğlence & Abonelik", "#e879f9", List.of("NETFLIX", "SPOTIFY", "YOUTUBE", "XBOX", "PLAYSTATION", "STEAM",
					"APPLE.COM/BILL", "AMZNPRIME", "AMAZON PRIME", "DISNEY", "BILETIX", "TVPLUS", "EXXEN", "BLUTV", "ANTHROPIC",
					"OPENAI", "GOOGLE *")),
			new Def("Fatura", "#94a3b8", List.of("TTNET", "TURK TELEKOM", "TURKCELL", "VODAFONE", "SUPERONLINE", "ENERJISA", "IGDAS",
					"ISKI", "ISTANBUL SU", "CK BOGAZICI", "AYEDAS", "DOGALGAZ")),
			new Def("Sigorta", "#2dd4bf", List.of("SIGORTA", "ALLIANZ", "HDI ", "AXA ", "SIG.PRIMI")),
			new Def("Seyahat", "#22d3ee", List.of("AIRBNB", "BOOKING", "HOTEL", "OTEL ", "AJET", "PEGASUS", "TURK HAVA YOLLARI", "THY ",
					"SUNEXPRESS", "ENUYGUN", "OBILET")),
			new Def("Online Alışveriş", "#fbbf24", List.of("HEPSIBURADA", "TRENDYOL", "AMAZON", "N11", "CICEKSEPETI")),
			new Def("Ev & Yaşam", "#a3e635", List.of("IKEA", "KOCTAS", "BAUHAUS", "ENGLISH HOME", "MADAME COCO", "KARACA")),
			new Def("Nakit Avans", "#fda4af", List.of("NAKIT AVANS", "NAKIT CEKIM")),
			new Def(FEES, "#64748b", List.of("FAIZ", "KKDF", "BSMV", "UYELIK UCRETI")),
			new Def("Diğer", "#475569", List.of()));

	private CategoryDefaults() {
	}

	static Map<String, Def> byName() {
		var m = new LinkedHashMap<String, Def>();
		CATEGORIES.forEach(d -> m.put(d.name(), d));
		return m;
	}
}
