package ar.edu.utn.frc.tup.piv.llm.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frc.tup.piv.llm.infrastructure.gateway.ProviderLlmGateway.Provider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProviderLlmGatewayTest {
  private HttpServer server;
  private String baseUrl;
  private final ProviderLlmGateway gateway = new ProviderLlmGateway(new ObjectMapper());

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1/models", ex -> reply(ex, 200, "{\"data\":[{\"id\":\"zeta\"},{\"id\":\"alfa\"},{\"id\":\"\"}]}"));
    server.createContext("/v1/chat/completions", ex -> {
      String req = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      if (req.contains("\"stream\":true")) {
        reply(ex, 200, "data: {\"choices\":[{\"delta\":{\"content\":\"ho\"}}]}\n\ndata: {\"choices\":[{\"delta\":{\"content\":\"la\"}}]}\n\ndata: {\"choices\":[{\"delta\":{}}]}\n\ndata: [DONE]\n");
      } else if (req.contains("boom")) {
        reply(ex, 500, "{}");
      } else if (req.contains("garbage")) {
        reply(ex, 200, "no-json");
      } else {
        reply(ex, 200, "{\"choices\":[{\"message\":{\"content\":\"hola\"}}],\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":5}}");
      }
    });
    server.start();
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private static void reply(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws java.io.IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    ex.sendResponseHeaders(status, bytes.length);
    ex.getResponseBody().write(bytes);
    ex.close();
  }

  private String unknownPathBase() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/nada";
  }

  @Test
  void listsModelsSortedAndSkippingBlanks() {
    assertThat(gateway.listModels(Provider.OPENAI_COMPATIBLE, baseUrl, "secret-key")).containsExactly("alfa", "zeta");
    assertThat(gateway.listModels(Provider.OPENAI_COMPATIBLE, baseUrl.replace("/v1", "") + "/", "secret-key")).hasSize(2);
  }

  @Test
  void chatReturnsTextAndTokenUsage() {
    var reply = gateway.chat(Provider.OPENAI_COMPATIBLE, baseUrl, "secret-key", "modelo", "hola");
    assertThat(reply.text()).isEqualTo("hola");
    assertThat(reply.inputTokens()).isEqualTo(3);
    assertThat(reply.outputTokens()).isEqualTo(5);
  }

  @Test
  void chatWrapsProviderFailuresAndInvalidBodies() {
    assertThatThrownBy(() -> gateway.chat(Provider.OPENAI_COMPATIBLE, baseUrl, "k", "m", "boom"))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("no pudo responder");
    assertThatThrownBy(() -> gateway.chat(Provider.OPENAI_COMPATIBLE, baseUrl, "k", "m", "garbage"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> gateway.listModels(Provider.OPENAI_COMPATIBLE, unknownPathBase(), "k"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void streamChatEmitsDeltasAndAccumulatesText() {
    List<String> deltas = new ArrayList<>();
    var reply = gateway.streamChat(Provider.OPENAI_COMPATIBLE, baseUrl, "k", "m", "hola", deltas::add);
    assertThat(deltas).containsExactly("ho", "la");
    assertThat(reply.text()).isEqualTo("hola");
  }

  @Test
  void streamChatFailsOnProviderError() {
    assertThatThrownBy(() -> gateway.streamChat(Provider.OPENAI_COMPATIBLE, unknownPathBase(), "k", "m", "hola", d -> { }))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("transmitir");
  }

  @Test
  void validatesBaseUrlIsPublicHttps() {
    assertThatThrownBy(() -> gateway.validateBaseUrl("http://api.example.com")).hasMessageContaining("HTTPS");
    assertThatThrownBy(() -> gateway.validateBaseUrl("https://127.0.0.1/v1")).hasMessageContaining("red privada");
    assertThatThrownBy(() -> gateway.validateBaseUrl("https://10.0.0.5/v1")).hasMessageContaining("red privada");
    assertThatThrownBy(() -> gateway.validateBaseUrl("no es url")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> gateway.validateBaseUrl("https://host-que-no-existe.invalid"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
