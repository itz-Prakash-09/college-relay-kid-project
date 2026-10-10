package com.relay.repository;

import com.relay.model.Booking;
import com.relay.model.BookingStatus;
import com.relay.model.TimeSlot;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe repository for managing Booking entities and querying temporal intervals.
 */
public class BookingRepository {
    private final Map<String, Booking> bookings = new ConcurrentHashMap<>();

    public void save(Booking booking) {
        if (booking == null) throw new IllegalArgumentException("Booking cannot be null");
        bookings.put(booking.getBookingId(), booking);
    }

    public Optional<Booking> findById(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(bookings.get(id));
    }

    public List<Booking> findAll() {
        return bookings.values().stream()
                .sorted(Comparator.comparing(b -> b.getTimeSlot().getStartTime()))
                .collect(Collectors.toList());
    }

    /**
     * Returns bookings that hold or reserve the kit (CONFIRMED, AT_RISK, or ACTIVE).
     */
    public List<Booking> findActiveOrUpcomingBookings() {
        return bookings.values().stream()
                .filter(b -> b.getStatus().isActiveOrUpcoming())
                .sorted(Comparator.comparing(b -> b.getTimeSlot().getStartTime()))
                .collect(Collectors.toList());
    }

    /**
     * Checks if any active/upcoming booking conflicts with the requested slot either by direct overlap
     * or by violating the minimum inspection interval.
     */
    public synchronized Optional<Booking> findFirstConflictingBooking(TimeSlot requestedSlot, Duration minGap) {
        return findActiveOrUpcomingBookings().stream()
                .filter(existing -> !existing.getTimeSlot().hasRequiredGap(requestedSlot, minGap))
                .findFirst();
    }

    /**
     * Finds all active or upcoming bookings that start after or around a given timestamp.
     */
    public List<Booking> findUpcomingFrom(LocalDateTime timestamp) {
        return findActiveOrUpcomingBookings().stream()
                .filter(b -> b.getTimeSlot().getEndTime().isAfter(timestamp))
                .sorted(Comparator.comparing(b -> b.getTimeSlot().getStartTime()))
                .collect(Collectors.toList());
    }

    public List<Booking> findByClub(String clubId) {
        if (clubId == null) return Collections.emptyList();
        return bookings.values().stream()
                .filter(b -> b.getClub().getId().equalsIgnoreCase(clubId.trim()))
                .sorted(Comparator.comparing(b -> b.getTimeSlot().getStartTime()))
                .collect(Collectors.toList());
    }

    public void clear() {
        bookings.clear();
    }
}
