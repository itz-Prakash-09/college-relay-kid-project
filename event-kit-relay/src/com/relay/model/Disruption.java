package com.relay.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Tracks equipment-related problems, such as missing or damaged items, and their resolution.
 */
public class Disruption {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String disruptionId;
    private final String itemId;
    private final ItemType itemType;
    private final DisruptionType type;
    private final String description;
    private final String originatingBookingId;
    private final LocalDateTime reportedAt;
    private LocalDateTime resolvedAt;
    private DisruptionStatus status;
    private final Set<String> affectedBookingIds;
    private String resolutionDetails;
    private String replacementItemId;

    public Disruption(String disruptionId, String itemId, ItemType itemType,
                      DisruptionType type, String description, String originatingBookingId) {
        if (disruptionId == null || disruptionId.isBlank()) throw new IllegalArgumentException("Disruption ID cannot be null or empty");
        if (itemId == null || itemId.isBlank()) throw new IllegalArgumentException("Item ID cannot be null or empty");
        if (itemType == null) throw new IllegalArgumentException("ItemType cannot be null");
        if (type == null) throw new IllegalArgumentException("DisruptionType cannot be null");

        this.disruptionId = disruptionId.trim();
        this.itemId = itemId.trim();
        this.itemType = itemType;
        this.type = type;
        this.description = description != null ? description.trim() : "Unspecified disruption";
        this.originatingBookingId = originatingBookingId != null && !originatingBookingId.isBlank() ? originatingBookingId.trim() : null;
        this.reportedAt = LocalDateTime.now();
        this.status = DisruptionStatus.OPEN;
        this.affectedBookingIds = Collections.synchronizedSet(new LinkedHashSet<>());
    }

    public synchronized String getDisruptionId() {
        return disruptionId;
    }

    public synchronized String getItemId() {
        return itemId;
    }

    public synchronized ItemType getItemType() {
        return itemType;
    }

    public synchronized DisruptionType getType() {
        return type;
    }

    public synchronized String getDescription() {
        return description;
    }

    public synchronized String getOriginatingBookingId() {
        return originatingBookingId;
    }

    public synchronized LocalDateTime getReportedAt() {
        return reportedAt;
    }

    public synchronized LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public synchronized DisruptionStatus getStatus() {
        return status;
    }

    public synchronized boolean isOpen() {
        return status == DisruptionStatus.OPEN;
    }

    public synchronized Set<String> getAffectedBookingIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(affectedBookingIds));
    }

    public synchronized void addAffectedBookingId(String bookingId) {
        if (bookingId != null && !bookingId.isBlank()) {
            this.affectedBookingIds.add(bookingId.trim());
        }
    }

    public synchronized String getResolutionDetails() {
        return resolutionDetails;
    }

    public synchronized String getReplacementItemId() {
        return replacementItemId;
    }

    /**
     * Resolves the disruption with details and optional replacement item.
     * Rule: Every disruption must have a recorded resolution or lead to the cancellation of an affected booking.
     */
    public synchronized void resolve(String resolutionDetails, String replacementItemId) {
        this.status = DisruptionStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
        this.resolutionDetails = resolutionDetails != null && !resolutionDetails.isBlank() ?
                resolutionDetails.trim() : "Resolved successfully";
        this.replacementItemId = replacementItemId != null && !replacementItemId.isBlank() ?
                replacementItemId.trim() : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Disruption that)) return false;
        return Objects.equals(disruptionId, that.disruptionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(disruptionId);
    }

    @Override
    public synchronized String toString() {
        return String.format("Disruption[%s | Type: %s | Item: %s (%s) | Status: %s | Reported: %s | Affected Bookings: %s%s]",
                disruptionId, type, itemId, itemType.getDisplayName(), status, reportedAt.format(FORMATTER),
                affectedBookingIds,
                status == DisruptionStatus.RESOLVED ? " | Resolved: " + resolutionDetails : "");
    }
}
