package com.relay;

import com.relay.dto.AvailabilityResult;
import com.relay.dto.InspectionResult;
import com.relay.exception.BookingConflictException;
import com.relay.exception.InspectionGapViolationException;
import com.relay.exception.KitNotAvailableException;
import com.relay.model.*;
import com.relay.service.KitRelayService;
import com.relay.service.KitRelayServiceImpl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Driver demonstration showcasing the complete end-to-end lifecycle
 * of the College Event Kit Relay system.
 */
public class Main {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static void main(String[] args) {
        printBanner();

        KitRelayService service = KitRelayServiceImpl.createDefault();
        LocalDate eventDate = LocalDate.now().plusDays(1);

        // -------------------------------------------------------------
        // STEP 1: Registration of College Clubs
        // -------------------------------------------------------------
        printSectionHeader("STEP 1: Registering Participating College Clubs");
        Club dramaClub = new Club("CLUB-DRAMA", "Drama & Theater Society", "Alex Rivers", "drama@college.edu", "555-0101");
        Club roboticsClub = new Club("CLUB-ROBOT", "Robotics Club", "Devin Vance", "robotics@college.edu", "555-0102");
        Club mediaClub = new Club("CLUB-MEDIA", "Media & Press Club", "Samira Khan", "press@college.edu", "555-0103");
        Club musicSociety = new Club("CLUB-MUSIC", "Music Society", "Chloe Bennett", "music@college.edu", "555-0104");

        service.registerClub(dramaClub);
        service.registerClub(roboticsClub);
        service.registerClub(mediaClub);
        service.registerClub(musicSociety);

        for (Club c : service.getAllClubs()) {
            System.out.println("  [Registered Club] " + c);
        }

        // -------------------------------------------------------------
        // STEP 2: Initial Kit Inspection
        // -------------------------------------------------------------
        printSectionHeader("STEP 2: Initial Kit Verification (inspectKit)");
        InspectionResult initialInspection = service.inspectKit();
        System.out.println("  Status: " + initialInspection);
        System.out.println("  Kit Details:\n" + initialInspection.getKit());

        // -------------------------------------------------------------
        // STEP 3: Booking Requests & Business Rules Enforcement
        // -------------------------------------------------------------
        printSectionHeader("STEP 3: Booking Creation & Conflict/Gap Validations (bookKit & checkAvailability)");

        // 3.1: Drama Club books 10:00 -> 12:00
        TimeSlot slot1 = new TimeSlot(eventDate.atTime(10, 0), eventDate.atTime(12, 0));
        System.out.println("\n[Request 1] Drama Club requests slot: " + slot1 + " for 'Annual Play Rehearsal'");
        Booking booking1 = service.bookKit(dramaClub.getId(), slot1, "Annual Play Rehearsal");
        System.out.println("  => Booking SUCCESS: " + booking1);

        // 3.2: Robotics Club requests overlapping slot: 11:30 -> 13:30 (Rule 1 Violation)
        TimeSlot overlapSlot = new TimeSlot(eventDate.atTime(11, 30), eventDate.atTime(13, 30));
        System.out.println("\n[Request 2 - Overlap Test] Robotics Club requests overlapping slot: " + overlapSlot);
        try {
            service.bookKit(roboticsClub.getId(), overlapSlot, "RoboWars Filming");
        } catch (BookingConflictException ex) {
            System.out.println("  => REJECTED AS EXPECTED (Rule 1): " + ex.getMessage());
        }

        // 3.3: Robotics Club requests back-to-back slot with only 5 min gap: 12:05 -> 14:00 (Rule 2 Violation)
        TimeSlot shortGapSlot = new TimeSlot(eventDate.atTime(12, 5), eventDate.atTime(14, 0));
        System.out.println("\n[Request 3 - Insufficient Gap Test] Robotics Club requests slot with 5 min gap: " + shortGapSlot);
        try {
            service.bookKit(roboticsClub.getId(), shortGapSlot, "RoboWars Filming");
        } catch (InspectionGapViolationException ex) {
            System.out.println("  => REJECTED AS EXPECTED (Rule 2): " + ex.getMessage());
        }

        // 3.4: Robotics Club requests slot with EXACT 15-minute gap: 12:15 -> 14:15 (Compliant!)
        TimeSlot validGapSlot = new TimeSlot(eventDate.atTime(12, 15), eventDate.atTime(14, 15));
        System.out.println("\n[Request 4 - Compliant Gap Test] Robotics Club requests slot with 15 min gap: " + validGapSlot);
        Booking booking2 = service.bookKit(roboticsClub.getId(), validGapSlot, "RoboWars Live Stream");
        System.out.println("  => Booking SUCCESS: " + booking2);

        // 3.5: Media Club requests afternoon slot: 15:00 -> 17:00
        TimeSlot afternoonSlot = new TimeSlot(eventDate.atTime(15, 0), eventDate.atTime(17, 0));
        System.out.println("\n[Request 5] Media Club requests slot: " + afternoonSlot + " for 'Campus News Recording'");
        Booking booking3 = service.bookKit(mediaClub.getId(), afternoonSlot, "Campus News Recording");
        System.out.println("  => Booking SUCCESS: " + booking3);

        // -------------------------------------------------------------
        // STEP 4: Equipment Handover - Checkout for Booking 1
        // -------------------------------------------------------------
        printSectionHeader("STEP 4: Equipment Checkout for Booking 1 (checkoutKit)");
        HandoverRecord checkout1 = service.checkoutKit(booking1.getBookingId(), "Prof. Vance (Kit Manager)", "Alex Rivers (Drama President)");
        System.out.println("Handover Record Created:\n" + checkout1);
        System.out.println("Updated Booking Status: " + booking1.getStatus());

        // -------------------------------------------------------------
        // STEP 5: Equipment Return with Damage Disruption
        // -------------------------------------------------------------
        printSectionHeader("STEP 5: Equipment Return with Damage (returnKit -> Disruption Triggered)");
        EventKit kit = service.getEventKit();
        KitItem camera = kit.getItem(ItemType.CAMERA);
        KitItem mic = kit.getItem(ItemType.MICROPHONE);
        KitItem tripod = kit.getItem(ItemType.TRIPOD);

        List<ItemConditionReport> returnReportsWithDamage = List.of(
                new ItemConditionReport(camera.getId(), ItemType.CAMERA, ItemCondition.EXCELLENT, "Clean and intact"),
                new ItemConditionReport(mic.getId(), ItemType.MICROPHONE, ItemCondition.EXCELLENT, "Transmitters and cables intact"),
                new ItemConditionReport(tripod.getId(), ItemType.TRIPOD, ItemCondition.DAMAGED, "Tripod leg clamp snapped during stage shoot")
        );

        HandoverRecord return1 = service.returnKit(
                booking1.getBookingId(),
                "Prof. Vance (Kit Manager)",
                "Alex Rivers (Drama President)",
                returnReportsWithDamage,
                "Tripod leg broken; camera and mic in excellent condition"
        );

        System.out.println("Return Handover Record Created:\n" + return1);
        System.out.println("Booking 1 Final Status: " + booking1.getStatus());

        // Verify Upcoming Bookings Marked AT_RISK (Rule 6)
        System.out.println("\n--- Impact on Upcoming Bookings (Rule 6 Verification) ---");
        System.out.println("  Booking 2 (" + booking2.getClub().getName() + "): Status = " + booking2.getStatus() + " | Risk: " + booking2.getRiskReason());
        System.out.println("  Booking 3 (" + booking3.getClub().getName() + "): Status = " + booking3.getStatus() + " | Risk: " + booking3.getRiskReason());

        // -------------------------------------------------------------
        // STEP 6: Blocked Checkout Due to Incomplete Kit
        // -------------------------------------------------------------
        printSectionHeader("STEP 6: Verifying Checkout Rejection for At-Risk Booking (Rule 4)");
        try {
            System.out.println("Robotics Club attempts to checkout while Tripod is DAMAGED...");
            service.checkoutKit(booking2.getBookingId(), "Prof. Vance", "Devin Vance");
        } catch (KitNotAvailableException ex) {
            System.out.println("  => CHECKOUT PREVENTED AS EXPECTED (Rule 4): " + ex.getMessage());
        }

        // -------------------------------------------------------------
        // STEP 7: Disruption Resolution & Reinstatement of At-Risk Bookings
        // -------------------------------------------------------------
        printSectionHeader("STEP 7: Resolving Disruption (resolveDisruption)");
        List<Disruption> openDisruptions = service.getOpenDisruptions();
        Disruption tripodDisruption = openDisruptions.get(0);
        System.out.println("Active Disruption Identified: " + tripodDisruption);

        System.out.println("\nProcuring replacement tripod: Heavy-Duty Carbon Video Tripod (ITEM-TRP-02)...");
        KitItem replacementTripod = new KitItem("ITEM-TRP-02", "Benro Professional Carbon Video Tripod",
                ItemType.TRIPOD, "SN-BNR-9988", ItemCondition.EXCELLENT, ItemStatus.AVAILABLE);

        service.resolveDisruption(tripodDisruption.getDisruptionId(),
                "Replaced broken tripod with new Benro Carbon Fiber Video Tripod from AV budget",
                replacementTripod);

        System.out.println("Disruption Status: " + service.getDisruption(tripodDisruption.getDisruptionId()).getStatus());
        System.out.println("Kit Inspection After Resolution: " + service.inspectKit());

        System.out.println("\n--- Automatic Reinstatement of Affected Bookings ---");
        System.out.println("  Booking 2 (" + booking2.getClub().getName() + ") Status: " + booking2.getStatus());
        System.out.println("  Booking 3 (" + booking3.getClub().getName() + ") Status: " + booking3.getStatus());

        // -------------------------------------------------------------
        // STEP 8: Successful Checkout & Clean Return of Restored Kit
        // -------------------------------------------------------------
        printSectionHeader("STEP 8: Successful Checkout & Clean Return for Robotics Club");
        HandoverRecord checkout2 = service.checkoutKit(booking2.getBookingId(), "Prof. Vance", "Devin Vance");
        System.out.println("Handover Record Created (Checkout #2):\n" + checkout2);

        List<ItemConditionReport> cleanReturnReports = List.of(
                new ItemConditionReport(camera.getId(), ItemType.CAMERA, ItemCondition.EXCELLENT, "Flawless"),
                new ItemConditionReport(mic.getId(), ItemType.MICROPHONE, ItemCondition.EXCELLENT, "Flawless"),
                new ItemConditionReport(replacementTripod.getId(), ItemType.TRIPOD, ItemCondition.EXCELLENT, "Flawless")
        );

        HandoverRecord return2 = service.returnKit(
                booking2.getBookingId(),
                "Prof. Vance",
                "Devin Vance",
                cleanReturnReports,
                "Returned on time with all accessories"
        );
        System.out.println("Handover Record Created (Return #2):\n" + return2);
        System.out.println("Booking 2 Final Status: " + booking2.getStatus());

        // -------------------------------------------------------------
        // STEP 9: Booking Cancellation Workflow
        // -------------------------------------------------------------
        printSectionHeader("STEP 9: Booking Cancellation Workflow (cancelBooking)");
        System.out.println("Media Club decides to cancel Booking 3 due to bad weather...");
        Booking cancelledBooking = service.cancelBooking(booking3.getBookingId(), "Outdoor shoot cancelled due to inclement weather");
        System.out.println("Cancelled Booking Status: " + cancelledBooking.getStatus() + " | Reason: " + cancelledBooking.getCancellationReason());

        // Verify the slot is now free for another club
        AvailabilityResult slotFreedResult = service.checkAvailability(afternoonSlot);
        System.out.println("Is slot " + afternoonSlot + " available again? " + slotFreedResult.isAvailable());

        // Music Society books the freed slot
        Booking booking4 = service.bookKit(musicSociety.getId(), afternoonSlot, "Studio Recording Session");
        System.out.println("Music Society immediately booked freed slot: " + booking4);

        printSummaryFooter(service);
    }

    private static void printBanner() {
        System.out.println("===============================================================================");
        System.out.println("        COLLEGE EVENT KIT RELAY - LOW-LEVEL DESIGN DEMONSTRATION");
        System.out.println("   Conflict-Free Sharing | 15-Min Gap | Handovers | Disruption Recovery");
        System.out.println("===============================================================================");
    }

    private static void printSectionHeader(String title) {
        System.out.println("\n" + "=".repeat(79));
        System.out.println("  " + title);
        System.out.println("=".repeat(79));
    }

    private static void printSummaryFooter(KitRelayService service) {
        printSectionHeader("SYSTEM AUDIT & FINAL STATE SUMMARY");
        System.out.println("Total Clubs: " + service.getAllClubs().size());
        System.out.println("Total Bookings: " + service.getAllBookings().size());
        for (Booking b : service.getAllBookings()) {
            System.out.println("  * " + b);
        }
        System.out.println("\nTotal Handover Records: " + service.getAllHandoverRecords().size());
        System.out.println("Total Disruptions Logged: " + service.getAllDisruptions().size());
        System.out.println("Open Disruptions Remaining: " + service.getOpenDisruptions().size());
        System.out.println("Final Kit Usability: " + (service.inspectKit().isReady() ? "100% OPERATIONAL [✓]" : "DEGRADED [✗]"));
        System.out.println("===============================================================================\n");
    }
}
