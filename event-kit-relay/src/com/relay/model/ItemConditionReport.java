package com.relay.model;

import java.util.Objects;

/**
 * Captures an item-level condition snapshot during checkout or return inspection.
 */
public class ItemConditionReport {
    private final String itemId;
    private final ItemType itemType;
    private final ItemCondition condition;
    private final String remarks;

    public ItemConditionReport(String itemId, ItemType itemType, ItemCondition condition, String remarks) {
        if (itemId == null || itemId.isBlank()) throw new IllegalArgumentException("Item ID cannot be null or empty");
        if (itemType == null) throw new IllegalArgumentException("ItemType cannot be null");
        if (condition == null) throw new IllegalArgumentException("ItemCondition cannot be null");
        this.itemId = itemId.trim();
        this.itemType = itemType;
        this.condition = condition;
        this.remarks = remarks != null ? remarks.trim() : "";
    }

    public String getItemId() {
        return itemId;
    }

    public ItemType getItemType() {
        return itemType;
    }

    public ItemCondition getCondition() {
        return condition;
    }

    public String getRemarks() {
        return remarks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemConditionReport that)) return false;
        return Objects.equals(itemId, that.itemId) &&
                itemType == that.itemType &&
                condition == that.condition;
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, itemType, condition);
    }

    @Override
    public String toString() {
        return String.format("[%s (%s): %s%s]",
                itemId, itemType.getDisplayName(), condition,
                remarks.isEmpty() ? "" : " - " + remarks);
    }
}
