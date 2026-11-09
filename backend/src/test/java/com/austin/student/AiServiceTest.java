package com.austin.student;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AiServiceTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void validatesStructuredOutputAndFallsBackWhenInvalid() throws Exception {
    var body =
        new AtomicReference<>(
            "{\"choices\":[{\"message\":{\"content\":\"{\\\"summary\\\":\\\"试听积极\\\",\\\"suggestedAction\\\":\\\"联系家长\\\"}\"}}]}");
    var request = new AtomicReference<String>();
    var authorization = new AtomicReference<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/chat/completions",
        ex -> {
          request.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          authorization.set(ex.getRequestHeaders().getFirst("Authorization"));
          byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
          ex.sendResponseHeaders(200, bytes.length);
          ex.getResponseBody().write(bytes);
          ex.close();
        });
    server.start();
    try {
      var service =
          new AiService(
              mapper,
              "test-key",
              "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
              "test-model");
      var good = service.suggest("积极参与");
      assertThat(good.get("available")).isEqualTo(true);
      assertThat(good.get("suggestedAction")).isEqualTo("联系家长");
      assertThat(authorization.get()).isEqualTo("Bearer test-key");
      var sent = mapper.readTree(request.get());
      assertThat(sent.path("response_format").path("type").asText()).isEqualTo("json_schema");
      assertThat(
              sent.path("response_format")
                  .path("json_schema")
                  .path("schema")
                  .path("properties")
                  .path("summary")
                  .path("maxLength")
                  .asInt())
          .isEqualTo(120);
      var localWithoutKey =
          new AiService(
              mapper,
              "",
              "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
              "test-model");
      assertThat(localWithoutKey.suggest("积极参与").get("available")).isEqualTo(true);
      assertThat(authorization.get()).isNull();
      String tooLong =
          mapper.writeValueAsString(Map.of("summary", "过".repeat(121), "suggestedAction", "联系家长"));
      body.set(
          mapper.writeValueAsString(
              Map.of("choices", List.of(Map.of("message", Map.of("content", tooLong))))));
      assertThat(localWithoutKey.suggest("积极参与").get("available")).isEqualTo(false);
      body.set(
          "{\"choices\":[{\"message\":{\"content\":\"{\\\"summary\\\":\\\"ok\\\",\\\"suggestedAction\\\":\\\"go\\\",\\\"extra\\\":true}\"}}]}");
      assertThat(service.suggest("积极参与").get("available")).isEqualTo(false);
      assertThat(
              new AiService(mapper, "", "https://api.openai.com/v1", "x")
                  .suggest("内容")
                  .get("available"))
          .isEqualTo(false);
      assertThat(
              new AiService(mapper, "test-key", "http://example.com", "x")
                  .suggest("内容")
                  .get("available"))
          .isEqualTo(false);
    } finally {
      server.stop(0);
    }
  }
}
