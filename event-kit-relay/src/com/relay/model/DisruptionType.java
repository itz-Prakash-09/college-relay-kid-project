package com.relay.model;

/**
 * Categorization of disruptions affecting equipment.
 */
public enum DisruptionType {
    MISSING("Equipment Missing"),
    DAMAGED("Equipment Damaged"),
    DEFECTIVE("Equipment Malfunction");

    private final String description;

    DisruptionType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
