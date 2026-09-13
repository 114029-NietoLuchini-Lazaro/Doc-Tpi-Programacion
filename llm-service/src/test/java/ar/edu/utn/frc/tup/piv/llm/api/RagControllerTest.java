package ar.edu.utn.frc.tup.piv.llm.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.tup.piv.llm.application.RagChatService;
import ar.edu.utn.frc.tup.piv.llm.application.RagIngestionService;
import ar.edu.utn.frc.tup.piv.llm.domain.rag.DiagramDecodeResult;
import ar.edu.utn.frc.tup.piv.llm.domain.rag.DocumentChunk;
import ar.edu.utn.frc.tup.piv.llm.domain.rag.ImageDetection;
import ar.edu.utn.frc.tup.piv.llm.domain.rag.RagDocument;
import ar.edu.utn.frc.tup.piv.llm.security.CallerIdentity;
import ar.edu.utn.frc.tup.piv.llm.security.RagGatewayAuthorization;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class RagControllerTest {
  private final CallerIdentity actor = new CallerIdentity("practice-service", UUID.randomUUID(), null, null);

  @Test
  void authorizesBeforeListingDocuments() {
    var ingestion = mock(RagIngestionService.class);
    var authorization = mock(RagGatewayAuthorization.class);
    var headers = new HttpHeaders();
    when(authorization.require(headers)).thenReturn(actor);
    UUID courseCohortId = UUID.randomUUID();
    var expected = List.of(sampleDocument());
    when(ingestion.list(courseCohortId)).thenReturn(expected);
    var controller = new RagController(ingestion, mock(RagChatService.class), authorization);

    var result = controller.listDocuments(courseCohortId, headers);

    assertThat(result).isEqualTo(expected);
    verify(authorization).require(headers);
  }

  @Test
  void authorizesAndUploadsADocument() throws Exception {
    var ingestion = mock(RagIngestionService.class);
    var authorization = mock(RagGatewayAuthorization.class);
    var headers = new HttpHeaders();
    when(authorization.require(headers)).thenReturn(actor);
    UUID courseCohortId = UUID.randomUUID();
    var file = new MockMultipartFile("file", "docker.pdf", "application/pdf", "contenido".getBytes());
    var expected = sampleDocument();
    when(ingestion.upload(courseCohortId, "docker.pdf", file.getBytes())).thenReturn(expected);
    var controller = new RagController(ingestion, mock(RagChatService.class), authorization);

    var response = controller.uploadDocument(courseCohortId, file, headers);

    assertThat(response.getStatusCode().value()).isEqualTo(201);
    assertThat(response.getBody()).isEqualTo(expected);
    var order = Mockito.inOrder(authorization, ingestion);
    order.verify(authorization).require(headers);
    order.verify(ingestion).upload(courseCohortId, "docker.pdf", file.getBytes());
  }

  @Test
  void rejectsAnEmptyUploadWithoutCallingTheService() {
    var ingestion = mock(RagIngestionService.class);
    var authorization = mock(RagGatewayAuthorization.class);
    var headers = new HttpHeaders();
    when(authorization.require(headers)).thenReturn(actor);
    var emptyFile = new MockMultipartFile("file", "docker.pdf", "application/pdf", new byte[0]);
    var controller = new RagController(ingestion, mock(RagChatService.class), authorization);

    assertThatThrownBy(() -> controller.uploadDocument(UUID.randomUUID(), emptyFile, headers))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void authorizesBeforeDeletingADocument() {
    var ingestion = mock(RagIngestionService.class);
    var authorization = mock(RagGatewayAuthorization.class);
    var headers = new HttpHeaders();
    when(authorization.require(headers)).thenReturn(actor);
    UUID id = UUID.randomUUID();
    var controller = new RagController(ingestion, mock(RagChatService.class), authorization);

    var response = controller.deleteDocument(id, headers);

    assertThat(response.getStatusCode().value()).isEqualTo(204);
    var order = Mockito.inOrder(authorization, ingestion);
    order.verify(authorization).require(headers);
    order.verify(ingestion).deactivate(id);
  }

  @Test
  void returnsChunksOfADocument() {
    var ingestion = mock(RagIngestionService.class);
    var authorization = mock(RagGatewayAuthorization.class);
    var headers = new HttpHeaders();
    when(authorization.require(headers)).thenReturn(actor);
    UUID id = UUID.randomUUID();
    var expected = List.of(new DocumentChunk(UUID.randomUUID(), id, "doc.pdf", 1, 0, "contenido", 0.0));
    when(ingestion.getChunks(id)).thenReturn(expected);
    var controller = new RagController(ingestion, mock(RagChatService.class), authorization);

    assertThat(controller.chunks(id, headers)).isEqualTo(expected);
  }

  @Test
  void returnsImagesWhenTheDocumentHasPdfBytes() {
    var ingestion = mock(RagIngestionService.class);
    var authorization = mock(RagGatewayAuthorization.class);
    var headers = new HttpHeaders();
    when(authorization.require(headers)).thenReturn(actor);
    UUID id = UUID.randomUUID();
    byte[] bytes = "pdf".getBytes();
    when(ingestion.getPdfBytes(id)).thenReturn(Optional.of(bytes));
    var expected = List.of(new ImageDetection(0, 1, 200, 200, "png", "data:...", "Figura 1"));
    when(ingestion.detectImages(bytes)).thenReturn(expected);
    var controller = new RagController(ingestion, mock(RagChatService.class), authorization);

    assertThat(controller.images(id, headers)).isEqualTo(expected);
  }

  @Test
  void imagesReturns404WhenTheDocumentHasNoPdfBytes() {
    var ingestion = mock(RagIngestionService.class);
    var authorization = mock(RagGatewayAuthorization.class);
    var headers = new HttpHeaders();
    when(authorization.require(headers)).thenReturn(actor);
    UUID id = UUID.randomUUID();
    when(ingestion.getPdfBytes(id)).thenReturn(Optional.empty());
    var controller = new RagController(ingestion, mock(RagChatService.class), authorization);

    assertThatThrownBy(() -> controller.images(id, headers))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
    verify(ingestion, never()).detectImages(any());
  }

  @Test
  void decodesAnImageWhenTheDocumentHasPdfBytes() {
    var ingestion = mock(RagIngestionService.class);
    var authorization = mock(RagGatewayAuthorization.class);
    var headers = new HttpHeaders();
    when(authorization.require(headers)).thenReturn(actor);
    UUID id = UUID.randomUUID();
    byte[] bytes = "pdf".getBytes();
    when(ingestion.getPdfBytes(id)).thenReturn(Optional.of(bytes));
    var expected = DiagramDecodeResult.vacio(0);
    when(ingestion.decodeImage(bytes, 0)).thenReturn(expected);
    var controller = new RagController(ingestion, mock(RagChatService.class), authorization);

    assertThat(controller.decodeImage(id, 0, headers)).isEqualTo(expected);
  }

  @Test
  void authorizesBeforeIndexingADiagram() {
    var ingestion = mock(RagIngestionService.class);
    var authorization = mock(RagGatewayAuthorization.class);
    var headers = new HttpHeaders();
    when(authorization.require(headers)).thenReturn(actor);
    UUID id = UUID.randomUUID();
    var result = DiagramDecodeResult.vacio(0);
    var controller = new RagController(ingestion, mock(RagChatService.class), authorization);

    var response = controller.indexDiagram(id, result, headers);

    assertThat(response.getStatusCode().value()).isEqualTo(201);
    verify(ingestion).indexDiagram(id, result);
  }

  @Test
  void authorizesBeforeChatting() {
    var chat = mock(RagChatService.class);
    var authorization = mock(RagGatewayAuthorization.class);
    var headers = new HttpHeaders();
    when(authorization.require(headers)).thenReturn(actor);
    var idempotencyKey = UUID.randomUUID();
    var body = new RagController.ChatRequest(UUID.randomUUID(), UUID.randomUUID(), List.of(UUID.randomUUID()), "¿qué es Docker?", null);
    var expected = new RagChatService.Response("respuesta", "OK", null, 5, false, "Profesor Tutor Pedagógico", List.of(), UUID.randomUUID());
    when(chat.responder(any(), org.mockito.ArgumentMatchers.eq(idempotencyKey), org.mockito.ArgumentMatchers.eq(actor))).thenReturn(expected);
    var controller = new RagController(mock(RagIngestionService.class), chat, authorization);

    var response = controller.chat(body, idempotencyKey, headers);

    assertThat(response).isEqualTo(expected);
    var order = Mockito.inOrder(authorization, chat);
    order.verify(authorization).require(headers);
    order.verify(chat).responder(any(), org.mockito.ArgumentMatchers.eq(idempotencyKey), org.mockito.ArgumentMatchers.eq(actor));
  }

  private RagDocument sampleDocument() {
    return new RagDocument(UUID.randomUUID(), UUID.randomUUID(), "docker.pdf", 1000, 5, 3, OffsetDateTime.now(), "preview", true);
  }
}
