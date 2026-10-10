package com.relay.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the collection of required equipment and verifies whether the complete kit is ready for use.
 */
public class EventKit {
    private final String id;
    private final String name;
    private final Set<ItemType> requiredTypes;
    private final Map<ItemType, KitItem> items;

    public EventKit(String id, String name) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Kit ID cannot be null or empty");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Kit name cannot be null or empty");
        this.id = id.trim();
        this.name = name.trim();
        this.requiredTypes = EnumSet.of(ItemType.CAMERA, ItemType.MICROPHONE, ItemType.TRIPOD);
        this.items = new ConcurrentHashMap<>();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Set<ItemType> getRequiredTypes() {
        return Collections.unmodifiableSet(requiredTypes);
    }

    public void registerOrReplaceItem(KitItem item) {
        if (item == null) throw new IllegalArgumentException("Item cannot be null");
        items.put(item.getType(), item);
    }

    public KitItem getItem(ItemType type) {
        return items.get(type);
    }

    public Optional<KitItem> getItemById(String itemId) {
        if (itemId == null) return Optional.empty();
        return items.values().stream()
                .filter(i -> i.getId().equalsIgnoreCase(itemId.trim()))
                .findFirst();
    }

    public Collection<KitItem> getAllItems() {
        return Collections.unmodifiableCollection(items.values());
    }

    /**
     * Verifies whether the complete kit is ready for use.
     * Rule: The kit can be issued only when all required items are available and usable.
     */
    public synchronized boolean isCompleteAndReady() {
        for (ItemType reqType : requiredTypes) {
            KitItem item = items.get(reqType);
            if (item == null || !item.isUsable()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a list of problematic items (missing from kit, or damaged/missing condition).
     */
    public synchronized List<String> getDefectsOrMissingReasons() {
        List<String> issues = new ArrayList<>();
        for (ItemType reqType : requiredTypes) {
            KitItem item = items.get(reqType);
            if (item == null) {
                issues.add("Missing required component: " + reqType.getDisplayName());
            } else if (!item.isUsable()) {
                issues.add(String.format("%s (%s) is not usable. Condition: %s, Status: %s, Notes: %s",
                        item.getName(), item.getType().getDisplayName(), item.getCondition(), item.getStatus(), item.getNotes()));
            }
        }
        return issues;
    }

    public synchronized void markAllIssued() {
        for (KitItem item : items.values()) {
            item.markIssued();
        }
    }

    public synchronized void markAllAvailable() {
        for (KitItem item : items.values()) {
            if (item.isUsable()) {
                item.markAvailable();
            }
        }
    }

    public synchronized void markAllUnderInspection() {
        for (KitItem item : items.values()) {
            item.markUnderInspection();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("EventKit[%s - '%s'] Ready: %s\n", id, name, isCompleteAndReady()));
        for (ItemType type : requiredTypes) {
            KitItem item = items.get(type);
            sb.append("  - ").append(type.getDisplayName()).append(": ");
            if (item != null) {
                sb.append(item.getName())
                  .append(" [Condition: ").append(item.getCondition())
                  .append(", Status: ").append(item.getStatus())
                  .append(", Usable: ").append(item.isUsable()).append("]");
            } else {
                sb.append("NOT ASSIGNED");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
