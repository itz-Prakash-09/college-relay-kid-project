package com.relay.model;

/**
 * Defines the standard types of equipment included in the event kit.
 */
public enum ItemType {
    CAMERA("Camera"),
    MICROPHONE("Microphone"),
    TRIPOD("Tripod");

    private final String displayName;

    ItemType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
