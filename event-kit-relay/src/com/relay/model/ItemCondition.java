package com.relay.model;

/**
 * Represents the physical condition of a kit item.
 */
public enum ItemCondition {
    EXCELLENT(true),
    GOOD(true),
    FAIR(true),
    DAMAGED(false),
    DEFECTIVE(false),
    MISSING(false);

    private final boolean usable;

    ItemCondition(boolean usable) {
        this.usable = usable;
    }

    /**
     * Determines whether equipment in this condition is safe and functional for checkout.
     */
    public boolean isUsable() {
        return usable;
    }
}
