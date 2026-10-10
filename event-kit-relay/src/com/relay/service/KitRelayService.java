package com.relay.service;

import com.relay.dto.AvailabilityResult;
import com.relay.dto.InspectionResult;
import com.relay.model.*;

import java.time.Duration;
import java.util.List;

/**
 * Acts as the central service responsible for booking validation, equipment handovers,
 * inspections, disruption management, and cancellations.
 */
public interface KitRelayService {

    Duration MANDATORY_INSPECTION_GAP = Duration.ofMinutes(15);

    // ==========================================
    // Core Operations specified in LLD
    // ==========================================

    /**
     * Validates and creates a booking for a club in the requested time slot.
     */
    Booking bookKit(String clubId, TimeSlot timeSlot, String eventName);

    /**
     * Verifies time-slot availability and enforces the mandatory 15-minute inspection gap.
     */
    AvailabilityResult checkAvailability(TimeSlot timeSlot);

    /**
     * Verifies equipment readiness, issues the kit, and records the checkout handover.
     */
    HandoverRecord checkoutKit(String bookingId, String organizerName, String clubRepresentative);

    /**
     * Records returned equipment, verifies item conditions, creates return handover record,
     * and automatically logs disruptions if any item is damaged or missing.
     */
    HandoverRecord returnKit(String bookingId, String organizerName, String clubRepresentative,
                             List<ItemConditionReport> returnReports, String remarks);

    /**
     * Verifies whether the complete kit (camera, microphone, tripod) is present, undamaged, and ready for use.
     */
    InspectionResult inspectKit();

    /**
     * Records missing or damaged items, marks affected upcoming bookings as AT_RISK, and alerts clubs.
     */
    Disruption reportDisruption(String itemId, DisruptionType type, String description, String originatingBookingId);

    /**
     * Records recovery or replacement of an item, restores kit readiness, and reinstates at-risk bookings.
     */
    void resolveDisruption(String disruptionId, String resolutionDetails, KitItem replacementItem);

    /**
     * Cancels an unfulfillable or requested booking and notifies the club.
     */
    Booking cancelBooking(String bookingId, String reason);

    // ==========================================
    // Management & Query Helpers
    // ==========================================

    void registerClub(Club club);
    Club getClub(String clubId);
    List<Club> getAllClubs();

    Booking getBooking(String bookingId);
    List<Booking> getAllBookings();
    List<Booking> getActiveOrUpcomingBookings();

    EventKit getEventKit();

    Disruption getDisruption(String disruptionId);
    List<Disruption> getAllDisruptions();
    List<Disruption> getOpenDisruptions();

    List<HandoverRecord> getHandoverRecords(String bookingId);
    List<HandoverRecord> getAllHandoverRecords();
}
