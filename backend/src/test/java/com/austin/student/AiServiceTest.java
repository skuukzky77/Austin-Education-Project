package com.austin.student;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/chat/completions",
        ex -> {
          request.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
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
      var sent = mapper.readTree(request.get());
      assertThat(sent.path("response_format").path("type").asText()).isEqualTo("json_schema");
      body.set(
          "{\"choices\":[{\"message\":{\"content\":\"{\\\"summary\\\":\\\"ok\\\",\\\"suggestedAction\\\":\\\"go\\\",\\\"extra\\\":true}\"}}]}");
      assertThat(service.suggest("积极参与").get("available")).isEqualTo(false);
      assertThat(new AiService(mapper, "", "http://localhost", "x").suggest("内容").get("available"))
          .isEqualTo(false);
    } finally {
      server.stop(0);
    }
  }
}
