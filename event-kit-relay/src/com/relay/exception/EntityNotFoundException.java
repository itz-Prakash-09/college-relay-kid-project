package com.relay.exception;

/**
 * Thrown when an entity (Club, Booking, KitItem, Disruption) is not found.
 */
public class EntityNotFoundException extends KitRelayException {
    public EntityNotFoundException(String message) {
        super(message);
    }
}
