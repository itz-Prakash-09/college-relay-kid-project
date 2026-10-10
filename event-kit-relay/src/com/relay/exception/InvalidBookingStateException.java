package com.relay.exception;

/**
 * Thrown when an illegal state transition is attempted on a booking.
 */
public class InvalidBookingStateException extends KitRelayException {
    public InvalidBookingStateException(String message) {
        super(message);
    }
}
