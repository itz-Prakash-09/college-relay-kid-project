package com.relay.exception;

/**
 * Thrown when the event kit cannot be issued because one or more required items are missing or not usable.
 */
public class KitNotAvailableException extends KitRelayException {
    public KitNotAvailableException(String message) {
        super(message);
    }
}
