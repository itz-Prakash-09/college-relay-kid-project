package com.relay.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Maintains a record of equipment issued and returned, along with inspection details.
 */
public class HandoverRecord {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String recordId;
    private final String bookingId;
    private final HandoverType type;
    private final LocalDateTime timestamp;
    private final String handledBy; // Organizer
    private final String clubRepresentative;
    private final List<ItemConditionReport> itemSnapshots;
    private final String remarks;

    public HandoverRecord(String recordId, String bookingId, HandoverType type,
                          String handledBy, String clubRepresentative,
                          List<ItemConditionReport> itemSnapshots, String remarks) {
        if (recordId == null || recordId.isBlank()) throw new IllegalArgumentException("Record ID cannot be null or empty");
        if (bookingId == null || bookingId.isBlank()) throw new IllegalArgumentException("Booking ID cannot be null or empty");
        if (type == null) throw new IllegalArgumentException("HandoverType cannot be null");
        if (handledBy == null || handledBy.isBlank()) throw new IllegalArgumentException("Handled by (organizer) cannot be null or empty");
        if (clubRepresentative == null || clubRepresentative.isBlank()) throw new IllegalArgumentException("Club representative cannot be null or empty");

        this.recordId = recordId.trim();
        this.bookingId = bookingId.trim();
        this.type = type;
        this.timestamp = LocalDateTime.now();
        this.handledBy = handledBy.trim();
        this.clubRepresentative = clubRepresentative.trim();
        this.itemSnapshots = itemSnapshots != null ? new ArrayList<>(itemSnapshots) : new ArrayList<>();
        this.remarks = remarks != null ? remarks.trim() : "";
    }

    public String getRecordId() {
        return recordId;
    }

    public String getBookingId() {
        return bookingId;
    }

    public HandoverType getType() {
        return type;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getHandledBy() {
        return handledBy;
    }

    public String getClubRepresentative() {
        return clubRepresentative;
    }

    public List<ItemConditionReport> getItemSnapshots() {
        return Collections.unmodifiableList(itemSnapshots);
    }

    public String getRemarks() {
        return remarks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HandoverRecord that)) return false;
        return Objects.equals(recordId, that.recordId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recordId);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("HandoverRecord[%s | Type: %s | Booking: %s | Time: %s | Organizer: %s | Rep: %s]\n",
                recordId, type, bookingId, timestamp.format(FORMATTER), handledBy, clubRepresentative));
        sb.append("  Inspected Items:\n");
        for (ItemConditionReport r : itemSnapshots) {
            sb.append("    * ").append(r).append("\n");
        }
        if (!remarks.isEmpty()) {
            sb.append("  Remarks: ").append(remarks).append("\n");
        }
        return sb.toString();
    }
}
