package com.relay.exception;

/**
 * Thrown when a requested reservation time-slot directly overlaps with an existing reservation.
 */
public class BookingConflictException extends KitRelayException {
    public BookingConflictException(String message) {
        super(message);
    }
}
