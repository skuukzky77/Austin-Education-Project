package com.austin.student;

import org.springframework.http.HttpStatus;

public class AppException extends RuntimeException {
  final HttpStatus status;

  AppException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  static AppException bad(String message) {
    return new AppException(HttpStatus.BAD_REQUEST, message);
  }

  static AppException forbidden() {
    return new AppException(HttpStatus.FORBIDDEN, "没有权限执行此操作");
  }

  static AppException missing(String message) {
    return new AppException(HttpStatus.NOT_FOUND, message);
  }

  static AppException conflict(String message) {
    return new AppException(HttpStatus.CONFLICT, message);
  }
}
