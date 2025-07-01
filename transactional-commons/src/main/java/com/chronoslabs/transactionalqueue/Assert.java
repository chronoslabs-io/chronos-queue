package com.chronoslabs.transactionalqueue;

import java.util.Objects;

public final class Assert {
  private Assert() {}

  public static void isTrue(boolean expression, String message) {
    if (!expression) {
      throw new IllegalArgumentException(message);
    }
  }

  public static <T> T requireNonNull(T object, String objectName) {
    Objects.requireNonNull(object, requireNonNullMessage(objectName));
    return object;
  }

  @SuppressWarnings("unchecked")
  public static <T> T requireInstanceOf(Class<T> type, Object object, String objectName) {
    requireNonNull(type, "Type to check against");
    if (!Checks.isInstanceOf(type, object)) {
      throw new IllegalArgumentException(
          String.format("%s must be an instance of %s", objectName, type.getCanonicalName()));
    }
    return ((T) object);
  }

  private static String requireNonNullMessage(String name) {
    return String.format("%s must not be null", name);
  }
}
