package com.example.demo.common.upload;

import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

/** Belgelerden düz metin çıkarır. Şimdilik PDF; diğer biçimler gerektikçe eklenir. */
public final class DocumentText {

	private DocumentText() {
	}

	/**
	 * PDF'in metnini sayfa sırasıyla döner. Satırlar konuma göre sıralanır; böylece tablo satırları
	 * (tarih, açıklama, tutar) aynı satırda kalır.
	 *
	 * @throws IllegalArgumentException PDF açılamazsa ya da şifreliyse
	 */
	public static String pdf(byte[] content) {
		try (var doc = Loader.loadPDF(content)) {
			if (doc.isEncrypted()) {
				throw new IllegalArgumentException("PDF şifreli; şifresiz bir kopya yükle");
			}
			var stripper = new PDFTextStripper();
			stripper.setSortByPosition(true);
			return stripper.getText(doc);
		}
		catch (IOException e) {
			throw new IllegalArgumentException("PDF okunamadı: " + e.getMessage(), e);
		}
	}
}
