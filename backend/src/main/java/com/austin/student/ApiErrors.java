package com.austin.student;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(AppException.class)
  ResponseEntity<Map<String, String>> app(AppException e) {
    return ResponseEntity.status(e.status).body(Map.of("error", e.getMessage()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<Map<String, String>> integrity(DataIntegrityViolationException e) {
    return ResponseEntity.status(409).body(Map.of("error", "数据发生冲突，请刷新后重试"));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<Map<String, String>> malformed(HttpMessageNotReadableException e) {
    return ResponseEntity.badRequest().body(Map.of("error", "请求格式有误"));
  }
}
