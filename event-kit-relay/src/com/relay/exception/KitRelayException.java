package com.relay.exception;

/**
 * Base unchecked exception for College Event Kit Relay domain errors.
 */
public class KitRelayException extends RuntimeException {
    public KitRelayException(String message) {
        super(message);
    }

    public KitRelayException(String message, Throwable cause) {
        super(message, cause);
    }
}
