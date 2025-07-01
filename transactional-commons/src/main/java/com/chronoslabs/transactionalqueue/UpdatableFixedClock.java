package com.chronoslabs.transactionalqueue;

import static com.chronoslabs.transactionalqueue.Assert.requireNonNull;
import static java.time.ZoneOffset.UTC;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

public class UpdatableFixedClock extends Clock {

  public static final Instant DEFAULT_FIXED_TIME = Instant.parse("2023-10-01T12:00:00.000Z");
  public static final ZoneId DEFAULT_ZONE_ID = UTC;

  public static UpdatableFixedClock defaultUpdatableFixedClock() {
    return updatableFixedClock(DEFAULT_FIXED_TIME);
  }

  public static UpdatableFixedClock updatableFixedClock(String defaultTime) {
    return updatableFixedClock(Instant.parse(defaultTime));
  }

  public static UpdatableFixedClock updatableFixedClock(Instant defaultTime) {
    return updatableFixedClock(defaultTime, DEFAULT_ZONE_ID);
  }

  public static UpdatableFixedClock updatableFixedClock(Instant defaultTime, ZoneId zoneId) {
    return new UpdatableFixedClock(defaultTime, zoneId);
  }

  private final Instant defaultTime;
  private final ZoneId zoneId;
  private Instant now;

  private UpdatableFixedClock(Instant defaultTime, ZoneId zoneId) {
    this.defaultTime = requireNonNull(defaultTime, "defaultTime");
    this.zoneId = requireNonNull(zoneId, "zoneId");
    this.now = defaultTime;
  }

  @Override
  public ZoneId getZone() {
    return zoneId;
  }

  @Override
  public UpdatableFixedClock withZone(ZoneId zone) {
    return new UpdatableFixedClock(this.defaultTime, zone).nowIs(now);
  }

  @Override
  public Instant instant() {
    return now;
  }

  public UpdatableFixedClock nowIs(String now) {
    return nowIs(Instant.parse(now));
  }

  public UpdatableFixedClock nowIs(Instant now) {
    this.now = now;
    return this;
  }

  public UpdatableFixedClock reset() {
    nowIs(defaultTime);
    return this;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof UpdatableFixedClock)) return false;

    UpdatableFixedClock other = (UpdatableFixedClock) obj;
    return now.equals(other.now) && zoneId.equals(other.zoneId);
  }

  @Override
  public int hashCode() {
    return now.hashCode() ^ zoneId.hashCode();
  }

  @Override
  public String toString() {
    return "UpdatableFixedClock[" + now + "," + zoneId + "]";
  }
}
