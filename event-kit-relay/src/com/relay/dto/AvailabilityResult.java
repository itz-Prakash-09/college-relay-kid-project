package com.relay.dto;

import com.relay.model.Booking;
import com.relay.model.TimeSlot;

/**
 * Result returned by availability verification operations.
 */
public class AvailabilityResult {
    private final boolean available;
    private final String reason;
    private final Booking conflictingBooking;
    private final TimeSlot requestedSlot;

    public AvailabilityResult(boolean available, String reason, Booking conflictingBooking, TimeSlot requestedSlot) {
        this.available = available;
        this.reason = reason;
        this.conflictingBooking = conflictingBooking;
        this.requestedSlot = requestedSlot;
    }

    public static AvailabilityResult available(TimeSlot slot) {
        return new AvailabilityResult(true, "Time slot is available with required inspection buffer", null, slot);
    }

    public static AvailabilityResult conflict(String reason, Booking conflictingBooking, TimeSlot slot) {
        return new AvailabilityResult(false, reason, conflictingBooking, slot);
    }

    public boolean isAvailable() {
        return available;
    }

    public String getReason() {
        return reason;
    }

    public Booking getConflictingBooking() {
        return conflictingBooking;
    }

    public TimeSlot getRequestedSlot() {
        return requestedSlot;
    }

    @Override
    public String toString() {
        if (available) {
            return String.format("AvailabilityResult[AVAILABLE: slot=%s]", requestedSlot);
        } else {
            return String.format("AvailabilityResult[UNAVAILABLE: slot=%s, reason='%s', conflictingBookingId=%s]",
                    requestedSlot, reason, conflictingBooking != null ? conflictingBooking.getBookingId() : "N/A");
        }
    }
}
