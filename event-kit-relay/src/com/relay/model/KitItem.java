package com.relay.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents an individual piece of equipment and maintains its current condition and availability.
 */
public class KitItem {
    private final String id;
    private final String name;
    private final ItemType type;
    private String serialNumber;
    private ItemCondition condition;
    private ItemStatus status;
    private String notes;
    private LocalDateTime lastInspectedAt;

    public KitItem(String id, String name, ItemType type, String serialNumber, ItemCondition condition, ItemStatus status) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Item ID cannot be null or empty");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Item name cannot be null or empty");
        if (type == null) throw new IllegalArgumentException("ItemType cannot be null");
        this.id = id.trim();
        this.name = name.trim();
        this.type = type;
        this.serialNumber = serialNumber != null ? serialNumber.trim() : "";
        this.condition = condition != null ? condition : ItemCondition.EXCELLENT;
        this.status = status != null ? status : ItemStatus.AVAILABLE;
        this.notes = "Initial stock";
        this.lastInspectedAt = LocalDateTime.now();
    }

    public synchronized String getId() {
        return id;
    }

    public synchronized String getName() {
        return name;
    }

    public synchronized ItemType getType() {
        return type;
    }

    public synchronized String getSerialNumber() {
        return serialNumber;
    }

    public synchronized ItemCondition getCondition() {
        return condition;
    }

    public synchronized ItemStatus getStatus() {
        return status;
    }

    public synchronized String getNotes() {
        return notes;
    }

    public synchronized LocalDateTime getLastInspectedAt() {
        return lastInspectedAt;
    }

    /**
     * Checks if the item is physically usable and available for checkout.
     */
    public synchronized boolean isUsable() {
        return condition.isUsable() &&
               status != ItemStatus.DAMAGED &&
               status != ItemStatus.MISSING &&
               status != ItemStatus.MAINTENANCE;
    }

    public synchronized void markIssued() {
        this.status = ItemStatus.ISSUED;
    }

    public synchronized void markAvailable() {
        this.status = ItemStatus.AVAILABLE;
        this.lastInspectedAt = LocalDateTime.now();
    }

    public synchronized void markUnderInspection() {
        this.status = ItemStatus.UNDER_INSPECTION;
        this.lastInspectedAt = LocalDateTime.now();
    }

    public synchronized void markDamaged(String reason) {
        this.condition = ItemCondition.DAMAGED;
        this.status = ItemStatus.DAMAGED;
        this.notes = reason;
        this.lastInspectedAt = LocalDateTime.now();
    }

    public synchronized void markMissing(String reason) {
        this.condition = ItemCondition.MISSING;
        this.status = ItemStatus.MISSING;
        this.notes = reason;
        this.lastInspectedAt = LocalDateTime.now();
    }

    public synchronized void updateCondition(ItemCondition newCondition, ItemStatus newStatus, String notes) {
        if (newCondition != null) this.condition = newCondition;
        if (newStatus != null) this.status = newStatus;
        if (notes != null) this.notes = notes;
        this.lastInspectedAt = LocalDateTime.now();
    }

    public synchronized void markRepairedOrReplaced(ItemCondition newCondition, String newSerialNumber, String notes) {
        this.condition = newCondition != null ? newCondition : ItemCondition.EXCELLENT;
        this.status = ItemStatus.AVAILABLE;
        if (newSerialNumber != null && !newSerialNumber.isBlank()) {
            this.serialNumber = newSerialNumber.trim();
        }
        this.notes = notes != null ? notes : "Recovered/Replaced item back in service";
        this.lastInspectedAt = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KitItem kitItem)) return false;
        return Objects.equals(id, kitItem.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public synchronized String toString() {
        return String.format("KitItem[%s - %s (%s), Condition: %s, Status: %s, SN: %s]",
                id, name, type, condition, status, serialNumber);
    }
}
