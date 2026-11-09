package com.austin.student;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApiController {
  private final Auth auth;
  private final StudentService students;
  private final AiService ai;

  ApiController(Auth auth, StudentService students, AiService ai) {
    this.auth = auth;
    this.students = students;
    this.ai = ai;
  }

  @PostMapping("/login")
  Object login(@RequestBody Map<String, Object> body, HttpServletRequest r) {
    return auth.login(
        StudentService.required(body, "username", "账号", 100),
        StudentService.required(body, "password", "密码", 200),
        r);
  }

  @PostMapping("/logout")
  Object logout(HttpServletRequest r) {
    var session = r.getSession(false);
    if (session != null) session.invalidate();
    return Map.of("ok", true);
  }

  @GetMapping("/me")
  Object me(HttpServletRequest r) {
    return auth.user(r);
  }

  @GetMapping("/teachers")
  Object teachers(HttpServletRequest r) {
    return students.teachers(auth.user(r));
  }

  @GetMapping("/students")
  Object list(@RequestParam(defaultValue = "") String q, HttpServletRequest r) {
    return students.students(auth.user(r), q);
  }

  @PostMapping("/students")
  Object create(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    return students.createStudent(auth.user(r), b);
  }

  @GetMapping("/students/{id}")
  Object student(@PathVariable long id, HttpServletRequest r) {
    return students.detail(auth.user(r), id);
  }

  @GetMapping("/dashboard")
  Object dashboard(HttpServletRequest r) {
    return students.dashboard(auth.user(r));
  }

  @GetMapping("/teacher/today")
  Object today(HttpServletRequest r) {
    return students.teacherToday(auth.user(r));
  }

  @PostMapping("/trials")
  Object createTrial(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    return students.createTrial(auth.user(r), b);
  }

  @PutMapping("/trials/{id}/schedule")
  Object reschedule(
      @PathVariable long id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    students.reschedule(auth.user(r), id, b);
    return Map.of("ok", true);
  }

  @PostMapping("/trials/{id}/cancel")
  Object cancel(@PathVariable long id, HttpServletRequest r) {
    students.cancel(auth.user(r), id);
    return Map.of("ok", true);
  }

  @PostMapping("/trials/{id}/complete")
  Object complete(@PathVariable long id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    students.completeTrial(auth.user(r), id, b);
    return Map.of("ok", true);
  }

  @PostMapping("/followups/{id}/complete")
  Object followup(@PathVariable long id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    students.completeFollowup(auth.user(r), id, b);
    return Map.of("ok", true);
  }

  @PostMapping("/ai/suggest")
  Object suggest(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    long student = StudentService.number(b, "studentId", "学生");
    students.detail(auth.user(r), student);
    return ai.suggest(StudentService.required(b, "notes", "试听反馈", 2000));
  }
}
