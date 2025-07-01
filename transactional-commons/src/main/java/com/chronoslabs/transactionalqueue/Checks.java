package com.chronoslabs.transactionalqueue;

public final class Checks {
  private Checks() {}

  public static boolean isInstanceOf(Class<?> type, Object obj) {
    return type != null && type.isInstance(obj);
  }
}
