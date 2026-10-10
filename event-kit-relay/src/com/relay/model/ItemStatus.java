package com.relay.model;

/**
 * Tracks the operational and inventory status of an item.
 */
public enum ItemStatus {
    AVAILABLE,
    ISSUED,
    UNDER_INSPECTION,
    DAMAGED,
    MISSING,
    MAINTENANCE
}
