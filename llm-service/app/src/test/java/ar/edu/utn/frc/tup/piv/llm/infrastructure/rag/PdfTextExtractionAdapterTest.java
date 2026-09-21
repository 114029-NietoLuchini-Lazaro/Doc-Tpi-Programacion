package ar.edu.utn.frc.tup.piv.llm.infrastructure.rag;

import ar.edu.utn.frc.tup.piv.llm.application.service.RagIngestionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class PdfTextExtractionAdapterTest {
  private final PdfTextExtractionAdapter adapter = new PdfTextExtractionAdapter();

  @Test
  void extractsTheTextOfEachPageWithItsPageNumber() throws Exception {
    byte[] pdf = pdfWithText("Docker comparte el kernel del sistema anfitrión.");

    var extracted = adapter.extractTextWithPages(pdf);

    assertThat(extracted.totalPages()).isEqualTo(1);
    assertThat(extracted.pages()).hasSize(1);
    assertThat(extracted.pages().get(0).pageNumber()).isEqualTo(1);
    assertThat(extracted.pages().get(0).text()).contains("Docker comparte el kernel");
    assertThat(extracted.fullText()).contains("Docker comparte el kernel");
  }

  @Test
  void rejectsNullOrEmptyBytes() {
    assertThatThrownBy(() -> adapter.extractTextWithPages(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adapter.extractTextWithPages(new byte[0])).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aPasswordProtectedPdfFailsAtLoadTimeBeforeTheEncryptedCheckCanRun() throws Exception {
    // Loader.loadPDF lanza InvalidPasswordException (una IOException) para un PDF con contraseña
    // real ANTES de que el adaptador llegue a chequear document.isEncrypted() — mismo
    // comportamiento heredado de demoLLMSpringAi/.../PdfTextExtractorService.java, no un bug
    // introducido acá. Por eso RagIngestionService.extractOrFail atrapa la IOException y la
    // traduce a un 422 claro en vez de dejarla escapar como 500 (ver esa clase).
    byte[] pdf = encryptedPdf();

    assertThatThrownBy(() -> adapter.extractTextWithPages(pdf)).isInstanceOf(IOException.class);
  }

  private byte[] pdfWithText(String text) throws IOException {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage();
      document.addPage(page);
      try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        stream.newLineAtOffset(50, 700);
        stream.showText(text);
        stream.endText();
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    }
  }

  private byte[] encryptedPdf() throws IOException {
    try (PDDocument document = new PDDocument()) {
      document.addPage(new PDPage());
      AccessPermission permission = new AccessPermission();
      StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-secret", "user-secret", permission);
      document.protect(policy);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    }
  }
}
