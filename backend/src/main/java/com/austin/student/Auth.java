package com.austin.student;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
public class Auth implements HandlerInterceptor {
  private final JdbcTemplate db;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

  Auth(JdbcTemplate db) {
    this.db = db;
  }

  public Map<String, Object> login(String username, String password, HttpServletRequest request) {
    if (username == null || password == null || username.length() > 100 || password.length() > 200)
      throw AppException.bad("请输入账号和密码");
    var found =
        db.queryForList(
            "SELECT id,username,name,role,password_hash,enabled FROM users WHERE username=?",
            username);
    if (found.isEmpty()
        || ((Number) found.getFirst().get("enabled")).intValue() != 1
        || !encoder.matches(password, (String) found.getFirst().get("password_hash")))
      throw new AppException(org.springframework.http.HttpStatus.UNAUTHORIZED, "账号或密码错误");
    var old = request.getSession(false);
    if (old != null) old.invalidate();
    request
        .getSession(true)
        .setAttribute("userId", ((Number) found.getFirst().get("id")).longValue());
    return publicUser(found.getFirst());
  }

  Map<String, Object> user(HttpServletRequest request) {
    Object user = request.getAttribute("currentUser");
    if (user == null)
      throw new AppException(org.springframework.http.HttpStatus.UNAUTHORIZED, "请先登录");
    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) user;
    return result;
  }

  private Map<String, Object> publicUser(Map<String, Object> row) {
    return Map.of(
        "id",
        row.get("id"),
        "username",
        row.get("username"),
        "name",
        row.get("name"),
        "role",
        row.get("role"));
  }

  @Override
  public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler)
      throws IOException {
    if (!List.of("GET", "HEAD", "OPTIONS").contains(req.getMethod())
        && !"XMLHttpRequest".equals(req.getHeader("X-Requested-With"))) {
      res.setStatus(403);
      res.setContentType("application/json;charset=UTF-8");
      res.getOutputStream().write("{\"error\":\"请求缺少必要的安全标头\"}".getBytes(StandardCharsets.UTF_8));
      return false;
    }
    if (req.getRequestURI().equals("/api/login")) return true;
    HttpSession session = req.getSession(false);
    Object id = session == null ? null : session.getAttribute("userId");
    List<Map<String, Object>> users =
        id == null
            ? List.of()
            : db.queryForList(
                "SELECT id,username,name,role FROM users WHERE id=? AND enabled=1", id);
    if (users.isEmpty()) {
      if (session != null) session.invalidate();
      res.setStatus(401);
      res.setContentType("application/json;charset=UTF-8");
      res.getOutputStream().write("{\"error\":\"请先登录\"}".getBytes(StandardCharsets.UTF_8));
      return false;
    }
    req.setAttribute("currentUser", publicUser(users.getFirst()));
    return true;
  }

  @Configuration
  static class Config implements WebMvcConfigurer {
    private final Auth auth;

    Config(Auth auth) {
      this.auth = auth;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
      registry.addInterceptor(auth).addPathPatterns("/api/**");
    }
  }
}
