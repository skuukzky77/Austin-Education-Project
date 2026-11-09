package com.austin.student;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class Times {
  public static final ZoneId ZONE = ZoneId.of("Australia/Melbourne");
  private static final DateTimeFormatter DISPLAY =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

  private Times() {}

  public static long parseLocal(String value) {
    try {
      LocalDateTime local = LocalDateTime.parse(value);
      List<ZoneOffset> offsets = ZONE.getRules().getValidOffsets(local);
      if (offsets.size() != 1) throw AppException.bad("墨尔本夏令时切换时该时间不存在或有歧义，请选择其他时间");
      return local.toInstant(offsets.getFirst()).toEpochMilli();
    } catch (AppException e) {
      throw e;
    } catch (Exception e) {
      throw AppException.bad("时间格式无效，请输入墨尔本当地日期和时间");
    }
  }

  public static String display(long epoch) {
    return Instant.ofEpochMilli(epoch).atZone(ZONE).format(DISPLAY);
  }
}
