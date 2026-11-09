package com.austin.student;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Opt-in: start scripts/start-local-model.sh before running this test. */
@EnabledIfEnvironmentVariable(named = "RUN_REAL_LLM_TEST", matches = "true")
class LocalModelIntegrationTest {
  @Test
  void producesValidatedStructuredOutputFromAnActualLocalModelWithoutAnApiKey() {
    var service = new AiService(new ObjectMapper(), "", "http://127.0.0.1:8081/v1", "qwen-local");
    var result = service.suggest("学生积极参加互动，能理解基础概念，但练习题准确率有待提高。家长希望周末联系确认课程安排。");
    assertThat(result.get("available")).isEqualTo(true);
    assertThat((String) result.get("summary")).isNotBlank().hasSizeLessThanOrEqualTo(300);
    assertThat((String) result.get("suggestedAction")).isNotBlank().hasSizeLessThanOrEqualTo(300);
  }
}
