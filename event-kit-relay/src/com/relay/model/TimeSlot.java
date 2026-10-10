package com.relay.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Represents a reservation time window with start and end timestamps.
 */
public class TimeSlot implements Comparable<TimeSlot> {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final LocalDateTime startTime;
    private final LocalDateTime endTime;

    public TimeSlot(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Start time and end time cannot be null");
        }
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException(String.format(
                    "Start time (%s) must be strictly before end time (%s)",
                    startTime.format(FORMATTER), endTime.format(FORMATTER)));
        }
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public Duration getDuration() {
        return Duration.between(startTime, endTime);
    }

    /**
     * Checks if two time slots overlap in time (share any non-zero duration).
     */
    public boolean overlapsWith(TimeSlot other) {
        if (other == null) return false;
        return this.startTime.isBefore(other.endTime) && other.startTime.isBefore(this.endTime);
    }

    /**
     * Enforces that consecutive bookings maintain at least the required buffer/gap between them.
     * Rule: A 15-minute inspection interval must be maintained between consecutive bookings.
     */
    public boolean hasRequiredGap(TimeSlot other, Duration minGap) {
        if (other == null) return true;
        if (this.overlapsWith(other)) {
            return false;
        }
        if (this.endTime.isBefore(other.startTime) || this.endTime.isEqual(other.startTime)) {
            Duration gap = Duration.between(this.endTime, other.startTime);
            return gap.compareTo(minGap) >= 0;
        } else if (other.endTime.isBefore(this.startTime) || other.endTime.isEqual(this.startTime)) {
            Duration gap = Duration.between(other.endTime, this.startTime);
            return gap.compareTo(minGap) >= 0;
        }
        return false;
    }

    /**
     * Calculates the idle gap between this slot and another disjoint slot.
     * Returns null if they overlap.
     */
    public Duration calculateGapBetween(TimeSlot other) {
        if (other == null || this.overlapsWith(other)) return null;
        if (this.endTime.isBefore(other.startTime) || this.endTime.isEqual(other.startTime)) {
            return Duration.between(this.endTime, other.startTime);
        } else {
            return Duration.between(other.endTime, this.startTime);
        }
    }

    @Override
    public int compareTo(TimeSlot o) {
        int cmp = this.startTime.compareTo(o.startTime);
        if (cmp != 0) return cmp;
        return this.endTime.compareTo(o.endTime);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TimeSlot timeSlot)) return false;
        return Objects.equals(startTime, timeSlot.startTime) && Objects.equals(endTime, timeSlot.endTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startTime, endTime);
    }

    @Override
    public String toString() {
        return String.format("[%s -> %s]", startTime.format(FORMATTER), endTime.format(FORMATTER));
    }
}
