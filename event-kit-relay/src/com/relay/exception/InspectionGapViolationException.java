package com.relay.exception;

/**
 * Thrown when the mandatory 15-minute inspection interval between consecutive bookings is violated.
 */
public class InspectionGapViolationException extends KitRelayException {
    public InspectionGapViolationException(String message) {
        super(message);
    }
}
