package io.chronoslabs.queue;

import java.time.Duration;
import java.util.Objects;

final class Assert {
  private Assert() {}

  static void isTrue(boolean expression, String message) {
    if (!expression) {
      throw new IllegalArgumentException(message);
    }
  }

  static void isPositive(Duration duration, String message) {
    isTrue(isPositive(duration), message);
  }

  static <T> T requireNonNull(T object, String objectName) {
    Objects.requireNonNull(object, requireNonNullMessage(objectName));
    return object;
  }

  private static String requireNonNullMessage(String name) {
    return String.format("%s must not be null", name);
  }

  private static boolean isPositive(Duration duration) {
    return (duration.getSeconds() | duration.getNano()) > 0;
  }
}
