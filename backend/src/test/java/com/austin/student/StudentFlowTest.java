package com.austin.student;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class StudentFlowTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) throws Exception {
    var path = Files.createTempFile("student-test-", ".db");
    path.toFile().deleteOnExit();
    r.add("spring.datasource.url", () -> "jdbc:sqlite:" + path);
  }

  MockHttpSession login(String username) throws Exception {
    var result =
        mvc.perform(
                post("/api/login")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of("username", username, "password", "demo1234"))))
            .andExpect(status().isOk())
            .andReturn();
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  MvcResult postJson(MockHttpSession session, String path, Object body) throws Exception {
    return mvc.perform(
            post(path)
                .session(session)
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
        .andReturn();
  }

  long student(MockHttpSession s, String name) throws Exception {
    MvcResult r =
        postJson(
            s,
            "/api/students",
            Map.of("name", name, "contactName", "家长", "phone", "0400000000", "relationship", "家长"));
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
  }

  String date(int days, String hour) {
    return LocalDate.now(Times.ZONE).plusDays(days) + "T" + hour;
  }

  @Test
  void authAndOwnerChecksApplyOnDirectApiCalls() throws Exception {
    mvc.perform(get("/api/students/1")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
    MockHttpSession first = login("counselor"),
        second = login("counselor2"),
        teacher = login("teacher");
    mvc.perform(get("/api/students/1").session(second)).andExpect(status().isForbidden());
    mvc.perform(get("/api/students/31").session(first)).andExpect(status().isForbidden());
    mvc.perform(get("/api/dashboard").session(teacher)).andExpect(status().isForbidden());
    mvc.perform(get("/api/teacher/today").session(first)).andExpect(status().isForbidden());
    mvc.perform(
            post("/api/trials/11/cancel")
                .session(first)
                .header("X-Requested-With", "XMLHttpRequest"))
        .andExpect(status().isForbidden());
  }

  @Test
  void trialToFollowupIsAtomicAndEnforcesState() throws Exception {
    var s = login("counselor");
    long id = student(s, "测试流程学生");
    var trial =
        postJson(
            s,
            "/api/trials",
            Map.of(
                "studentId",
                id,
                "teacherId",
                4,
                "startsAt",
                date(-10, "12:00"),
                "endsAt",
                date(-10, "13:00")));
    assertThat(trial.getResponse().getStatus()).isEqualTo(200);
    long tid = json.readTree(trial.getResponse().getContentAsString()).get("id").asLong();
    assertThat(
            postJson(
                    s,
                    "/api/trials/" + tid + "/complete",
                    Map.of("result", "INTERESTED", "action", "联系家长"))
                .getResponse()
                .getStatus())
        .isEqualTo(400);
    JsonNode before =
        json.readTree(
            mvc.perform(get("/api/students/" + id).session(s))
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(before.path("trials").get(0).path("status").asText()).isEqualTo("SCHEDULED");
    assertThat(
            postJson(
                    s,
                    "/api/trials/" + tid + "/complete",
                    Map.of("result", "INTERESTED", "action", "联系家长", "dueAt", date(1, "12:00")))
                .getResponse()
                .getStatus())
        .isEqualTo(200);
    assertThat(
            postJson(
                    s,
                    "/api/trials/" + tid + "/complete",
                    Map.of("result", "INTERESTED", "noFollowupReason", "无需跟进"))
                .getResponse()
                .getStatus())
        .isEqualTo(409);
    JsonNode after =
        json.readTree(
            mvc.perform(get("/api/students/" + id).session(s))
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(after.path("followups").size()).isEqualTo(1);
    long fid = after.path("followups").get(0).path("id").asLong();
    assertThat(
            postJson(s, "/api/followups/" + fid + "/complete", Map.of("note", "家长确认下周回复"))
                .getResponse()
                .getStatus())
        .isEqualTo(200);
    assertThat(
            postJson(s, "/api/followups/" + fid + "/complete", Map.of("note", "重复"))
                .getResponse()
                .getStatus())
        .isEqualTo(409);
    assertThat(
            postJson(s, "/api/ai/suggest", Map.of("studentId", id, "notes", "学生表现积极"))
                .getResponse()
                .getStatus())
        .isEqualTo(200);
  }

  @Test
  void schedulingChecksLessonsAndAllowsAdjacentIntervals() throws Exception {
    var s = login("counselor");
    long id = student(s, "测试冲突学生");
    var conflict =
        postJson(
            s,
            "/api/trials",
            Map.of(
                "studentId",
                id,
                "teacherId",
                3,
                "startsAt",
                date(1, "09:30"),
                "endsAt",
                date(1, "10:30")));
    assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
    var studentConflict =
        postJson(
            s,
            "/api/trials",
            Map.of(
                "studentId",
                11,
                "teacherId",
                4,
                "startsAt",
                date(1, "09:30"),
                "endsAt",
                date(1, "10:30")));
    assertThat(studentConflict.getResponse().getStatus()).isEqualTo(409);
    var adjacent =
        postJson(
            s,
            "/api/trials",
            Map.of(
                "studentId",
                id,
                "teacherId",
                3,
                "startsAt",
                date(1, "10:00"),
                "endsAt",
                date(1, "11:00")));
    assertThat(adjacent.getResponse().getStatus()).isEqualTo(200);
    var duplicate =
        postJson(
            s,
            "/api/trials",
            Map.of(
                "studentId",
                id,
                "teacherId",
                4,
                "startsAt",
                date(2, "10:00"),
                "endsAt",
                date(2, "11:00")));
    assertThat(duplicate.getResponse().getStatus()).isEqualTo(409);
  }

  @Test
  void dashboardSeparatesEndedTrialsUntilTheirResultsAreRecorded() throws Exception {
    var counselor = login("counselor");
    var otherCounselor = login("counselor2");
    long endedStudent = student(counselor, "待录入试听");
    long upcomingStudent = student(counselor, "未来试听");
    long otherStudent = student(otherCounselor, "其他顾问的试听");
    long cancelledStudent = student(counselor, "已取消试听");

    var ended =
        postJson(
            counselor,
            "/api/trials",
            Map.of(
                "studentId",
                endedStudent,
                "teacherId",
                4,
                "startsAt",
                date(-20, "12:00"),
                "endsAt",
                date(-20, "13:00")));
    var upcoming =
        postJson(
            counselor,
            "/api/trials",
            Map.of(
                "studentId",
                upcomingStudent,
                "teacherId",
                4,
                "startsAt",
                date(20, "12:00"),
                "endsAt",
                date(20, "13:00")));
    var other =
        postJson(
            otherCounselor,
            "/api/trials",
            Map.of(
                "studentId",
                otherStudent,
                "teacherId",
                4,
                "startsAt",
                date(-21, "12:00"),
                "endsAt",
                date(-21, "13:00")));
    var cancelled =
        postJson(
            counselor,
            "/api/trials",
            Map.of(
                "studentId",
                cancelledStudent,
                "teacherId",
                4,
                "startsAt",
                date(-22, "12:00"),
                "endsAt",
                date(-22, "13:00")));
    assertThat(ended.getResponse().getStatus()).isEqualTo(200);
    assertThat(upcoming.getResponse().getStatus()).isEqualTo(200);
    assertThat(other.getResponse().getStatus()).isEqualTo(200);
    assertThat(cancelled.getResponse().getStatus()).isEqualTo(200);

    long endedId = json.readTree(ended.getResponse().getContentAsString()).path("id").asLong();
    long upcomingId =
        json.readTree(upcoming.getResponse().getContentAsString()).path("id").asLong();
    long cancelledId =
        json.readTree(cancelled.getResponse().getContentAsString()).path("id").asLong();
    mvc.perform(
            post("/api/trials/" + cancelledId + "/cancel")
                .session(counselor)
                .header("X-Requested-With", "XMLHttpRequest"))
        .andExpect(status().isOk());
    JsonNode dashboard =
        json.readTree(
            mvc.perform(get("/api/dashboard").session(counselor))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(dashboard.path("pendingResults").findValuesAsText("studentName"))
        .contains("待录入试听")
        .doesNotContain("未来试听", "其他顾问的试听", "已取消试听");
    assertThat(dashboard.path("trials").findValuesAsText("studentName"))
        .contains("未来试听")
        .doesNotContain("待录入试听");

    assertThat(
            postJson(
                    counselor,
                    "/api/trials/" + endedId + "/complete",
                    Map.of("result", "INTERESTED", "noFollowupReason", "已当面确认无需跟进"))
                .getResponse()
                .getStatus())
        .isEqualTo(200);
    mvc.perform(
            post("/api/trials/" + upcomingId + "/cancel")
                .session(counselor)
                .header("X-Requested-With", "XMLHttpRequest"))
        .andExpect(status().isOk());
    JsonNode after =
        json.readTree(
            mvc.perform(get("/api/dashboard").session(counselor))
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(after.path("pendingResults").findValuesAsText("studentName"))
        .doesNotContain("待录入试听");
    assertThat(after.path("trials").findValuesAsText("studentName")).doesNotContain("未来试听");
  }

  @Test
  void concurrentBookingCannotDoubleBookTeacher() throws Exception {
    var s = login("counselor");
    long first = student(s, "并发一"), second = student(s, "并发二");
    var gate = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      Callable<Integer> a =
          () -> {
            gate.await();
            return postJson(
                    s,
                    "/api/trials",
                    Map.of(
                        "studentId",
                        first,
                        "teacherId",
                        4,
                        "startsAt",
                        date(10, "12:00"),
                        "endsAt",
                        date(10, "13:00")))
                .getResponse()
                .getStatus();
          };
      Callable<Integer> b =
          () -> {
            gate.await();
            return postJson(
                    s,
                    "/api/trials",
                    Map.of(
                        "studentId",
                        second,
                        "teacherId",
                        4,
                        "startsAt",
                        date(10, "12:00"),
                        "endsAt",
                        date(10, "13:00")))
                .getResponse()
                .getStatus();
          };
      var fa = pool.submit(a);
      var fb = pool.submit(b);
      gate.countDown();
      assertThat(new int[] {fa.get(10, TimeUnit.SECONDS), fb.get(10, TimeUnit.SECONDS)})
          .containsExactlyInAnyOrder(200, 409);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void rejectsAmbiguousAndNonexistentMelbourneTimes() {
    assertThatThrownBy(() -> Times.parseLocal("2026-10-04T02:30")).isInstanceOf(AppException.class);
    assertThatThrownBy(() -> Times.parseLocal("2026-04-05T02:30")).isInstanceOf(AppException.class);
  }
}
