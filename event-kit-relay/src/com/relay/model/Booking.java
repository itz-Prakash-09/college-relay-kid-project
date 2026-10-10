package com.relay.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Stores booking details, including the club, time slot, and booking status.
 */
public class Booking {
    private final String bookingId;
    private final Club club;
    private final String eventName;
    private final TimeSlot timeSlot;
    private BookingStatus status;
    private final LocalDateTime createdAt;
    private String cancellationReason;
    private String riskReason;
    private String associatedDisruptionId;

    public Booking(String bookingId, Club club, String eventName, TimeSlot timeSlot) {
        if (bookingId == null || bookingId.isBlank()) throw new IllegalArgumentException("Booking ID cannot be null or empty");
        if (club == null) throw new IllegalArgumentException("Club cannot be null");
        if (timeSlot == null) throw new IllegalArgumentException("TimeSlot cannot be null");
        this.bookingId = bookingId.trim();
        this.club = club;
        this.eventName = eventName != null && !eventName.isBlank() ? eventName.trim() : "Campus Event";
        this.timeSlot = timeSlot;
        this.status = BookingStatus.CONFIRMED;
        this.createdAt = LocalDateTime.now();
    }

    public synchronized String getBookingId() {
        return bookingId;
    }

    public synchronized Club getClub() {
        return club;
    }

    public synchronized String getEventName() {
        return eventName;
    }

    public synchronized TimeSlot getTimeSlot() {
        return timeSlot;
    }

    public synchronized BookingStatus getStatus() {
        return status;
    }

    public synchronized LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public synchronized String getCancellationReason() {
        return cancellationReason;
    }

    public synchronized String getRiskReason() {
        return riskReason;
    }

    public synchronized String getAssociatedDisruptionId() {
        return associatedDisruptionId;
    }

    public synchronized void markConfirmed() {
        this.status = BookingStatus.CONFIRMED;
        this.riskReason = null;
        this.associatedDisruptionId = null;
    }

    /**
     * Rule: Upcoming bookings affected by a disruption must be marked as at risk.
     */
    public synchronized void markAtRisk(String reason, String disruptionId) {
        this.status = BookingStatus.AT_RISK;
        this.riskReason = reason;
        this.associatedDisruptionId = disruptionId;
    }

    public synchronized void reinstateConfirmed() {
        if (this.status == BookingStatus.AT_RISK) {
            this.status = BookingStatus.CONFIRMED;
            this.riskReason = null;
            this.associatedDisruptionId = null;
        }
    }

    public synchronized void markActive() {
        this.status = BookingStatus.ACTIVE;
    }

    public synchronized void markCompleted() {
        this.status = BookingStatus.COMPLETED;
    }

    public synchronized void markCancelled(String reason) {
        this.status = BookingStatus.CANCELLED;
        this.cancellationReason = reason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Booking booking)) return false;
        return Objects.equals(bookingId, booking.bookingId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bookingId);
    }

    @Override
    public synchronized String toString() {
        return String.format("Booking[%s | Club: '%s' | Slot: %s | Status: %s | Event: '%s'%s]",
                bookingId, club.getName(), timeSlot, status, eventName,
                status == BookingStatus.AT_RISK ? " (RISK: " + riskReason + ")" :
                status == BookingStatus.CANCELLED ? " (CANCELLED: " + cancellationReason + ")" : "");
    }
}
