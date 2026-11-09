package com.austin.student;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class Seed implements CommandLineRunner {
  private final JdbcTemplate db;

  Seed(JdbcTemplate db) {
    this.db = db;
  }

  private long time(int day, int hour, int min) {
    return ZonedDateTime.of(
            LocalDate.now(Times.ZONE).plusDays(day), LocalTime.of(hour, min), Times.ZONE)
        .toInstant()
        .toEpochMilli();
  }

  @Override
  @Transactional
  public void run(String... args) {
    if (db.queryForObject("SELECT count(*) FROM users", Integer.class) > 0) return;
    String hash = new BCryptPasswordEncoder().encode("demo1234");
    db.update(
        "INSERT INTO users(id,username,name,role,password_hash)"
            + " VALUES(1,'counselor','林顾问','COUNSELOR',?),(2,'counselor2','陈顾问','COUNSELOR',?),(3,'teacher','王老师','TEACHER',?),(4,'teacher2','李老师','TEACHER',?)",
        hash,
        hash,
        hash,
        hash);
    long now = System.currentTimeMillis();
    String[] surnames = {
      "林", "陈", "王", "张", "李", "黄", "刘", "杨", "吴", "徐", "周", "赵", "许", "孙", "马", "朱", "胡", "郭", "何",
      "高", "罗", "郑", "梁", "谢", "宋", "唐", "韩", "冯", "于", "董"
    };
    for (int i = 1; i <= 40; i++) {
      String name = surnames[(i - 1) % surnames.length] + (i <= 30 ? "同学" : "小朋友");
      db.update(
          "INSERT INTO students(id,name,status,counselor_id,created_at) VALUES(?,?,"
              + " 'CONSULTING',?,?)",
          i,
          name,
          i <= 30 ? 1 : 2,
          now - i * 10000L);
      db.update(
          "INSERT INTO contacts(id,name,phone) VALUES(?,?,?)",
          i,
          name + "家长",
          String.format("04%08d", 20000000 + i));
      db.update(
          "INSERT INTO student_contacts(student_id,contact_id,relationship) VALUES(?,?,'家长')",
          i,
          i);
    }
    for (int i = 1; i <= 6; i++) {
      db.update(
          "INSERT INTO"
              + " trials(id,student_id,teacher_id,starts_at,ends_at,status,result,notes,created_at)"
              + " VALUES(?,?,3,?,?,'COMPLETED',?,'试听反馈：学习态度积极',?)",
          i,
          i,
          time(-i, 15, 0),
          time(-i, 16, 0),
          i % 2 == 0 ? "UNDECIDED" : "INTERESTED",
          now);
      db.update(
          "INSERT INTO followups(student_id,trial_id,counselor_id,due_at,status,action)"
              + " VALUES(?,?,1,?,'OPEN',?)",
          i,
          i,
          time(i - 3, 10, 0),
          "联系家长确认报名意向");
    }
    for (int i = 7; i <= 10; i++)
      db.update(
          "INSERT INTO trials(student_id,teacher_id,starts_at,ends_at,status,created_at)"
              + " VALUES(?,3,?,?,'SCHEDULED',?)",
          i,
          time(i - 6, 16, 0),
          time(i - 6, 17, 0),
          now);
    db.update(
        "INSERT INTO trials(student_id,teacher_id,starts_at,ends_at,status,created_at)"
            + " VALUES(31,4,?,?,'SCHEDULED',?)",
        time(1, 10, 0),
        time(1, 11, 0),
        now);
    db.update(
        "INSERT INTO trials(student_id,teacher_id,starts_at,ends_at,status,created_at)"
            + " VALUES(13,3,?,?,'SCHEDULED',?)",
        time(0, 20, 0),
        time(0, 21, 0),
        now);
    db.update(
        "INSERT INTO lessons(id,title,teacher_id,starts_at,ends_at,status)"
            + " VALUES(1,'数学固定班',3,?,?,'SCHEDULED')",
        time(1, 9, 0),
        time(1, 10, 0));
    db.update("INSERT INTO lesson_students(lesson_id,student_id) VALUES(1,11),(1,12)");
  }
}
