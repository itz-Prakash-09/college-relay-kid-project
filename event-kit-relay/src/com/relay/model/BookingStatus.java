package com.relay.model;

/**
 * Tracks the lifecycle status of an event kit booking.
 */
public enum BookingStatus {
    REQUESTED,
    CONFIRMED,
    AT_RISK,
    ACTIVE,
    COMPLETED,
    CANCELLED;

    public boolean isActiveOrUpcoming() {
        return this == CONFIRMED || this == AT_RISK || this == ACTIVE;
    }
}
