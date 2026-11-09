package com.austin.student;

import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentService {
  private final JdbcTemplate db;

  StudentService(JdbcTemplate db) {
    this.db = db;
  }

  static long id(Map<String, Object> u) {
    return ((Number) u.get("id")).longValue();
  }

  static void counselor(Map<String, Object> u) {
    if (!"COUNSELOR".equals(u.get("role"))) throw AppException.forbidden();
  }

  static String required(Map<String, Object> data, String key, String label, int max) {
    Object v = data.get(key);
    if (!(v instanceof String s) || s.isBlank() || s.trim().length() > max)
      throw AppException.bad(label + "不能为空且不能超过 " + max + " 个字符");
    return s.trim();
  }

  static String optional(Map<String, Object> data, String key, String label, int max) {
    Object v = data.get(key);
    if (v == null) return "";
    if (!(v instanceof String s) || s.trim().length() > max)
      throw AppException.bad(label + "格式有误或过长");
    return s.trim();
  }

  static long number(Map<String, Object> data, String key, String label) {
    Object v = data.get(key);
    if (!(v instanceof Number n) || n.doubleValue() != n.longValue() || n.longValue() <= 0)
      throw AppException.bad(label + "无效");
    return n.longValue();
  }

  private long insert(String sql, Object... args) {
    KeyHolder keys = new GeneratedKeyHolder();
    db.update(
        c -> {
          var ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
          return ps;
        },
        keys);
    Number key = keys.getKey();
    if (key == null) throw new IllegalStateException("Missing inserted ID");
    return key.longValue();
  }

  private Map<String, Object> one(String sql, String notFound, Object... args) {
    var rows = db.queryForList(sql, args);
    if (rows.isEmpty()) throw AppException.missing(notFound);
    return rows.getFirst();
  }

  private Map<String, Object> owned(Map<String, Object> user, long studentId) {
    counselor(user);
    var s = one("SELECT id,name,status,counselor_id FROM students WHERE id=?", "学生不存在", studentId);
    if (((Number) s.get("counselor_id")).longValue() != id(user)) throw AppException.forbidden();
    return s;
  }

  private void teacher(long teacherId) {
    Integer n =
        db.queryForObject(
            "SELECT count(*) FROM users WHERE id=? AND role='TEACHER' AND enabled=1",
            Integer.class,
            teacherId);
    if (n == null || n == 0) throw AppException.bad("请选择有效的老师");
  }

  private Map<String, Object> trial(Map<String, Object> user, long trialId) {
    var t = one("SELECT * FROM trials WHERE id=?", "试听不存在", trialId);
    owned(user, ((Number) t.get("student_id")).longValue());
    return t;
  }

  private void checkSchedule(long student, long teacher, long start, long end, Long exclude) {
    if (start >= end) throw AppException.bad("结束时间必须晚于开始时间");
    if (end - start > 8 * 3600_000L) throw AppException.bad("试听时长不能超过 8 小时");
    int count =
        db.queryForObject(
            "SELECT count(*) FROM trials WHERE status='SCHEDULED' AND id<>? AND starts_at<? AND"
                + " ends_at>? AND (student_id=? OR teacher_id=?)",
            Integer.class,
            exclude == null ? -1 : exclude,
            end,
            start,
            student,
            teacher);
    count +=
        db.queryForObject(
            "SELECT count(*) FROM lessons l WHERE l.status='SCHEDULED' AND l.starts_at<? AND"
                + " l.ends_at>? AND (l.teacher_id=? OR EXISTS (SELECT 1 FROM lesson_students ls"
                + " WHERE ls.lesson_id=l.id AND ls.student_id=?))",
            Integer.class,
            end,
            start,
            teacher,
            student);
    if (count > 0) throw AppException.conflict("该时间段学生或老师已有试听或正式课，请选择其他时间");
    int already =
        db.queryForObject(
            "SELECT count(*) FROM trials WHERE status='SCHEDULED' AND student_id=? AND id<>?",
            Integer.class,
            student,
            exclude == null ? -1 : exclude);
    if (already > 0) throw AppException.conflict("该学生已有一节待进行的试听");
  }

  public List<Map<String, Object>> teachers(Map<String, Object> user) {
    counselor(user);
    return db.queryForList(
        "SELECT id,name FROM users WHERE role='TEACHER' AND enabled=1 ORDER BY name");
  }

  public List<Map<String, Object>> students(Map<String, Object> user, String q) {
    counselor(user);
    String term = q == null ? "" : q.trim();
    if (term.length() > 100) throw AppException.bad("搜索词过长");
    String escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return db.queryForList(
        "SELECT s.id,s.name,s.status,s.counselor_id AS counselorId,u.name AS counselorName,CASE"
            + " WHEN s.counselor_id=? THEN 1 ELSE 0 END AS mine FROM students s JOIN users u ON"
            + " u.id=s.counselor_id WHERE s.name LIKE ? ESCAPE '\\' ORDER BY mine DESC,s.created_at"
            + " DESC LIMIT 200",
        id(user),
        "%" + escaped + "%");
  }

  @Transactional
  public Map<String, Object> createStudent(Map<String, Object> user, Map<String, Object> body) {
    counselor(user);
    String name = required(body, "name", "学生姓名", 80),
        contact = required(body, "contactName", "联系人", 80),
        phone = required(body, "phone", "联系电话", 40),
        rel = required(body, "relationship", "关系", 40);
    long student =
        insert(
            "INSERT INTO students(name,counselor_id,created_at) VALUES(?,?,?)",
            name,
            id(user),
            System.currentTimeMillis());
    long person = insert("INSERT INTO contacts(name,phone) VALUES(?,?)", contact, phone);
    db.update(
        "INSERT INTO student_contacts(student_id,contact_id,relationship) VALUES(?,?,?)",
        student,
        person,
        rel);
    return Map.of("id", student);
  }

  public Map<String, Object> detail(Map<String, Object> user, long studentId) {
    Map<String, Object> s = owned(user, studentId);
    var result = new LinkedHashMap<String, Object>();
    result.put("id", s.get("id"));
    result.put("name", s.get("name"));
    result.put("status", s.get("status"));
    result.put(
        "contacts",
        db.queryForList(
            "SELECT c.name,c.phone,sc.relationship FROM contacts c JOIN student_contacts sc ON"
                + " sc.contact_id=c.id WHERE sc.student_id=?",
            studentId));
    result.put("trials", trials("WHERE t.student_id=? ORDER BY t.starts_at DESC", studentId));
    result.put("followups", followups("WHERE f.student_id=? ORDER BY f.due_at", studentId));
    return result;
  }

  private List<Map<String, Object>> trials(String clause, Object... args) {
    var rows =
        db.queryForList(
            "SELECT t.id,t.student_id AS studentId,s.name AS studentName,t.teacher_id AS"
                + " teacherId,u.name AS teacherName,t.starts_at AS startsAt,t.ends_at AS"
                + " endsAt,t.status,t.result,t.notes,t.no_followup_reason AS noFollowupReason FROM"
                + " trials t JOIN students s ON s.id=t.student_id JOIN users u ON u.id=t.teacher_id"
                + " "
                + clause,
            args);
    for (var row : rows) {
      row.put("startsAt", Times.display(((Number) row.get("startsAt")).longValue()));
      row.put("endsAt", Times.display(((Number) row.get("endsAt")).longValue()));
    }
    return rows;
  }

  private List<Map<String, Object>> followups(String clause, Object... args) {
    var rows =
        db.queryForList(
            "SELECT f.id,f.student_id AS studentId,s.name AS studentName,f.trial_id AS"
                + " trialId,f.due_at AS dueAt,f.status,f.action,f.completion_note AS completionNote"
                + " FROM followups f JOIN students s ON s.id=f.student_id "
                + clause,
            args);
    for (var row : rows) row.put("dueAt", Times.display(((Number) row.get("dueAt")).longValue()));
    return rows;
  }

  public Map<String, Object> dashboard(Map<String, Object> user) {
    counselor(user);
    return Map.of(
        "followups",
        followups("WHERE f.counselor_id=? AND f.status='OPEN' ORDER BY f.due_at", id(user)),
        "trials",
        trials("WHERE s.counselor_id=? AND t.status='SCHEDULED' ORDER BY t.starts_at", id(user)),
        "studentCount",
        db.queryForObject(
            "SELECT count(*) FROM students WHERE counselor_id=?", Integer.class, id(user)));
  }

  public List<Map<String, Object>> teacherToday(Map<String, Object> user) {
    if (!"TEACHER".equals(user.get("role"))) throw AppException.forbidden();
    LocalDate today = LocalDate.now(Times.ZONE);
    long from = today.atStartOfDay(Times.ZONE).toInstant().toEpochMilli(),
        to = today.plusDays(1).atStartOfDay(Times.ZONE).toInstant().toEpochMilli();
    var result = new ArrayList<Map<String, Object>>();
    for (var t :
        trials(
            "WHERE t.teacher_id=? AND t.status='SCHEDULED' AND t.starts_at>=? AND t.starts_at<?"
                + " ORDER BY t.starts_at",
            id(user),
            from,
            to))
      result.add(
          Map.of(
              "type",
              "试听",
              "title",
              t.get("studentName"),
              "startsAt",
              t.get("startsAt"),
              "endsAt",
              t.get("endsAt")));
    var lessons =
        db.queryForList(
            "SELECT l.id,l.title,l.starts_at,l.ends_at FROM lessons l WHERE l.teacher_id=? AND"
                + " l.status='SCHEDULED' AND l.starts_at>=? AND l.starts_at<?",
            id(user),
            from,
            to);
    for (var l : lessons) {
      var names =
          db.queryForList(
              "SELECT s.name FROM students s JOIN lesson_students ls ON ls.student_id=s.id WHERE"
                  + " ls.lesson_id=? ORDER BY s.name",
              String.class,
              l.get("id"));
      result.add(
          Map.of(
              "type",
              "正式课",
              "title",
              l.get("title"),
              "startsAt",
              Times.display(((Number) l.get("starts_at")).longValue()),
              "endsAt",
              Times.display(((Number) l.get("ends_at")).longValue()),
              "students",
              names));
    }
    result.sort(Comparator.comparing(a -> (String) a.get("startsAt")));
    return result;
  }

  @Transactional
  public Map<String, Object> createTrial(Map<String, Object> user, Map<String, Object> body) {
    long student = number(body, "studentId", "学生"), teacher = number(body, "teacherId", "老师");
    owned(user, student);
    teacher(teacher);
    long start = Times.parseLocal(required(body, "startsAt", "开始时间", 32)),
        end = Times.parseLocal(required(body, "endsAt", "结束时间", 32));
    checkSchedule(student, teacher, start, end, null);
    return Map.of(
        "id",
        insert(
            "INSERT INTO trials(student_id,teacher_id,starts_at,ends_at,status,created_at)"
                + " VALUES(?,?,?,?,'SCHEDULED',?)",
            student,
            teacher,
            start,
            end,
            System.currentTimeMillis()));
  }

  @Transactional
  public void reschedule(Map<String, Object> user, long trialId, Map<String, Object> body) {
    var t = trial(user, trialId);
    if (!"SCHEDULED".equals(t.get("status"))) throw AppException.conflict("只能调整已安排的试听");
    long teacher = number(body, "teacherId", "老师"),
        student = ((Number) t.get("student_id")).longValue();
    teacher(teacher);
    long start = Times.parseLocal(required(body, "startsAt", "开始时间", 32)),
        end = Times.parseLocal(required(body, "endsAt", "结束时间", 32));
    checkSchedule(student, teacher, start, end, trialId);
    db.update(
        "UPDATE trials SET teacher_id=?,starts_at=?,ends_at=? WHERE id=?",
        teacher,
        start,
        end,
        trialId);
  }

  @Transactional
  public void cancel(Map<String, Object> user, long trialId) {
    var t = trial(user, trialId);
    if (!"SCHEDULED".equals(t.get("status"))) throw AppException.conflict("只能取消已安排的试听");
    db.update("UPDATE trials SET status='CANCELLED' WHERE id=?", trialId);
  }

  @Transactional
  public void completeTrial(Map<String, Object> user, long trialId, Map<String, Object> body) {
    var t = trial(user, trialId);
    if (!"SCHEDULED".equals(t.get("status"))) throw AppException.conflict("只能为已安排的试听记录结果");
    if (((Number) t.get("ends_at")).longValue() > System.currentTimeMillis())
      throw AppException.conflict("试听结束后才能记录结果");
    String result = required(body, "result", "试听结果", 32);
    if (!Set.of("INTERESTED", "UNDECIDED", "NOT_INTERESTED").contains(result))
      throw AppException.bad("试听结果无效");
    String notes = optional(body, "notes", "试听反馈", 2000),
        action = optional(body, "action", "跟进计划", 300),
        reason = optional(body, "noFollowupReason", "无需跟进的原因", 300);
    if ((action.isEmpty()) == reason.isEmpty())
      throw AppException.bad("请填写跟进计划，或填写无需跟进的原因，两者只能选一项");
    Long due = null;
    if (!action.isEmpty()) due = Times.parseLocal(required(body, "dueAt", "跟进时间", 32));
    db.update(
        "UPDATE trials SET status='COMPLETED',result=?,notes=?,no_followup_reason=? WHERE id=?",
        result,
        notes,
        reason.isEmpty() ? null : reason,
        trialId);
    if (due != null)
      db.update(
          "INSERT INTO followups(student_id,trial_id,counselor_id,due_at,status,action)"
              + " VALUES(?,?,?,?, 'OPEN',?)",
          t.get("student_id"),
          trialId,
          id(user),
          due,
          action);
  }

  @Transactional
  public void completeFollowup(
      Map<String, Object> user, long followupId, Map<String, Object> body) {
    counselor(user);
    var f = one("SELECT * FROM followups WHERE id=?", "跟进不存在", followupId);
    owned(user, ((Number) f.get("student_id")).longValue());
    if (((Number) f.get("counselor_id")).longValue() != id(user)) throw AppException.forbidden();
    if (!"OPEN".equals(f.get("status"))) throw AppException.conflict("该跟进已完成");
    String note = required(body, "note", "跟进记录", 1000);
    db.update(
        "UPDATE followups SET status='DONE',completion_note=?,completed_at=? WHERE id=?",
        note,
        System.currentTimeMillis(),
        followupId);
  }
}
