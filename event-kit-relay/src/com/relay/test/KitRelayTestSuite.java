package com.relay.test;

import com.relay.dto.AvailabilityResult;
import com.relay.dto.InspectionResult;
import com.relay.exception.BookingConflictException;
import com.relay.exception.InspectionGapViolationException;
import com.relay.exception.InvalidBookingStateException;
import com.relay.exception.KitNotAvailableException;
import com.relay.model.*;
import com.relay.notification.ConsoleNotificationService;
import com.relay.service.KitRelayService;
import com.relay.service.KitRelayServiceImpl;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Comprehensive automated test suite verifying all 7 business rules and main operations.
 */
public class KitRelayTestSuite {

    private static int totalTests = 0;
    private static int passedTests = 0;

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("  COLLEGE EVENT KIT RELAY - COMPREHENSIVE TEST SUITE EXECUTION");
        System.out.println("=================================================================\n");

        runTest("Test 1: Booking Availability and Non-Overlapping Bookings", KitRelayTestSuite::testNonOverlappingBookings);
        runTest("Test 2: Mandatory 15-Minute Inspection Gap Enforcement", KitRelayTestSuite::testFifteenMinuteInspectionGap);
        runTest("Test 3: Inspection Readiness and Usability Validation", KitRelayTestSuite::testInspectionReadiness);
        runTest("Test 4: Checkout Handover Workflow and Item Status Tracking", KitRelayTestSuite::testCheckoutWorkflow);
        runTest("Test 5: Return Handover and Clean Complete Cycle", KitRelayTestSuite::testReturnCleanWorkflow);
        runTest("Test 6: Damage on Return Automatically Triggers Disruption and Marks Upcoming At-Risk", KitRelayTestSuite::testDamageOnReturnAndAtRisk);
        runTest("Test 7: Damaged Kit Blocks New Checkouts", KitRelayTestSuite::testDamagedKitBlocksCheckout);
        runTest("Test 8: Disruption Resolution Restores Kit and Reinstates At-Risk Bookings", KitRelayTestSuite::testDisruptionResolutionReinstatesBooking);
        runTest("Test 9: Cancellation of Booking When Kit Cannot Be Made Available", KitRelayTestSuite::testCancellationWorkflow);
        runTest("Test 10: Invalid State Transitions Safeguards", KitRelayTestSuite::testInvalidStateTransitions);

        System.out.println("\n-----------------------------------------------------------------");
        System.out.printf("  TEST EXECUTION SUMMARY: %d / %d TESTS PASSED (%.1f%%)\n",
                passedTests, totalTests, (totalTests > 0 ? (passedTests * 100.0 / totalTests) : 0));
        System.out.println("-----------------------------------------------------------------");

        if (passedTests == totalTests) {
            System.out.println(">>> ALL BUSINESS RULES AND SPECIFICATIONS VERIFIED SUCCESSFULLY! <<<");
        } else {
            System.err.println(">>> SOME TESTS FAILED. PLEASE REVIEW FAILURE LOGS ABOVE. <<<");
            System.exit(1);
        }
    }

    private static void runTest(String testName, Runnable testMethod) {
        totalTests++;
        System.out.printf("[RUNNING] %s ... ", testName);
        try {
            testMethod.run();
            passedTests++;
            System.out.println("PASSED [✓]");
        } catch (Throwable t) {
            System.out.println("FAILED [✗]");
            System.err.println("  -> Error: " + t.getMessage());
            t.printStackTrace(System.err);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(String.format("%s - Expected: <%s> but got: <%s>", message, expected, actual));
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Assertion failed: " + message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError("Assertion failed: " + message);
        }
    }

    private static KitRelayService createTestService() {
        KitRelayServiceImpl service = KitRelayServiceImpl.createDefault();
        service.registerClub(new Club("CLUB-DRAMA", "Drama & Theater Society", "Alex Rivers", "drama@college.edu", "555-0101"));
        service.registerClub(new Club("CLUB-ROBOT", "Robotics Club", "Devin Vance", "robotics@college.edu", "555-0102"));
        service.registerClub(new Club("CLUB-MUSIC", "Music Society", "Chloe Bennett", "music@college.edu", "555-0103"));
        service.registerClub(new Club("CLUB-MEDIA", "Media & Press Club", "Samira Khan", "press@college.edu", "555-0104"));
        return service;
    }

    // -------------------------------------------------------------
    // Test Implementations
    // -------------------------------------------------------------

    private static void testNonOverlappingBookings() {
        KitRelayService service = createTestService();
        LocalDateTime base = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);

        TimeSlot slot1 = new TimeSlot(base, base.plusHours(2)); // 10:00 -> 12:00
        Booking b1 = service.bookKit("CLUB-DRAMA", slot1, "Annual Play Rehearsal");
        assertEquals(BookingStatus.CONFIRMED, b1.getStatus(), "Booking 1 should be confirmed");

        // Direct overlap attempt: 11:00 -> 13:00
        TimeSlot overlappingSlot = new TimeSlot(base.plusHours(1), base.plusHours(3));
        try {
            service.bookKit("CLUB-ROBOT", overlappingSlot, "RoboWars Filming");
            throw new AssertionError("Should have rejected overlapping booking");
        } catch (BookingConflictException e) {
            assertTrue(e.getMessage().contains("Overlaps directly"), "Error should explain direct overlap");
        }
    }

    private static void testFifteenMinuteInspectionGap() {
        KitRelayService service = createTestService();
        LocalDateTime base = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);

        TimeSlot slot1 = new TimeSlot(base, base.plusHours(2)); // 10:00 -> 12:00
        service.bookKit("CLUB-DRAMA", slot1, "Play Rehearsal");

        // Exactly 0 minute gap: 12:00 -> 14:00 (Should be rejected)
        TimeSlot zeroGapSlot = new TimeSlot(base.plusHours(2), base.plusHours(4));
        try {
            service.bookKit("CLUB-ROBOT", zeroGapSlot, "RoboWars Filming");
            throw new AssertionError("Should have rejected zero inspection gap");
        } catch (InspectionGapViolationException e) {
            assertTrue(e.getMessage().contains("15-minute"), "Should mention 15-minute gap requirement");
        }

        // 10 minute gap: 12:10 -> 14:00 (Should be rejected)
        TimeSlot tenMinGapSlot = new TimeSlot(base.plusHours(2).plusMinutes(10), base.plusHours(4));
        try {
            service.bookKit("CLUB-ROBOT", tenMinGapSlot, "RoboWars Filming");
            throw new AssertionError("Should have rejected 10-minute inspection gap");
        } catch (InspectionGapViolationException e) {
            assertTrue(e.getMessage().contains("10 minutes"), "Should calculate 10 minutes gap");
        }

        // Exactly 15 minute gap: 12:15 -> 14:00 (Should succeed)
        TimeSlot fifteenMinGapSlot = new TimeSlot(base.plusHours(2).plusMinutes(15), base.plusHours(4));
        Booking b2 = service.bookKit("CLUB-ROBOT", fifteenMinGapSlot, "RoboWars Filming");
        assertEquals(BookingStatus.CONFIRMED, b2.getStatus(), "15-minute gap booking should be confirmed");

        // Prior slot with 15-minute gap: 08:00 -> 09:45 (Should succeed)
        TimeSlot priorSlot = new TimeSlot(base.minusHours(2), base.minusMinutes(15));
        Booking b3 = service.bookKit("CLUB-MUSIC", priorSlot, "Morning Acoustic Session");
        assertEquals(BookingStatus.CONFIRMED, b3.getStatus(), "Prior slot with 15-minute gap should be confirmed");
    }

    private static void testInspectionReadiness() {
        KitRelayService service = createTestService();
        InspectionResult inspection = service.inspectKit();

        assertTrue(inspection.isReady(), "Initial kit should be complete and ready");
        assertEquals(3, inspection.getKit().getAllItems().size(), "Kit should have camera, mic, and tripod");
        assertTrue(inspection.getIssues().isEmpty(), "Initial kit should have zero issues");
    }

    private static void testCheckoutWorkflow() {
        KitRelayService service = createTestService();
        LocalDateTime base = LocalDateTime.now().plusHours(1);
        TimeSlot slot = new TimeSlot(base, base.plusHours(2));
        Booking booking = service.bookKit("CLUB-DRAMA", slot, "Auditions");

        HandoverRecord checkoutRecord = service.checkoutKit(booking.getBookingId(), "Prof. Miller (Staff)", "Alex Rivers (Rep)");

        assertEquals(HandoverType.CHECKOUT, checkoutRecord.getType(), "Record type must be CHECKOUT");
        assertEquals(booking.getBookingId(), checkoutRecord.getBookingId(), "Must match booking ID");
        assertEquals(3, checkoutRecord.getItemSnapshots().size(), "Must record condition for all 3 items");
        assertEquals(BookingStatus.ACTIVE, booking.getStatus(), "Booking must transition to ACTIVE");

        // Kit items should be marked ISSUED
        for (KitItem item : service.getEventKit().getAllItems()) {
            assertEquals(ItemStatus.ISSUED, item.getStatus(), "Item status must be ISSUED");
        }
    }

    private static void testReturnCleanWorkflow() {
        KitRelayService service = createTestService();
        LocalDateTime base = LocalDateTime.now().plusHours(1);
        TimeSlot slot = new TimeSlot(base, base.plusHours(2));
        Booking booking = service.bookKit("CLUB-DRAMA", slot, "Auditions");

        service.checkoutKit(booking.getBookingId(), "Prof. Miller", "Alex Rivers");

        EventKit kit = service.getEventKit();
        List<ItemConditionReport> returnReports = List.of(
                new ItemConditionReport(kit.getItem(ItemType.CAMERA).getId(), ItemType.CAMERA, ItemCondition.EXCELLENT, "Lens clean, battery charged"),
                new ItemConditionReport(kit.getItem(ItemType.MICROPHONE).getId(), ItemType.MICROPHONE, ItemCondition.EXCELLENT, "Both wireless transmitters returned"),
                new ItemConditionReport(kit.getItem(ItemType.TRIPOD).getId(), ItemType.TRIPOD, ItemCondition.EXCELLENT, "Fluid head smooth, bag included")
        );

        HandoverRecord returnRecord = service.returnKit(booking.getBookingId(), "Prof. Miller", "Alex Rivers", returnReports, "All equipment returned in pristine condition");

        assertEquals(HandoverType.RETURN, returnRecord.getType(), "Record type must be RETURN");
        assertEquals(BookingStatus.COMPLETED, booking.getStatus(), "Booking status must be COMPLETED");

        for (KitItem item : service.getEventKit().getAllItems()) {
            assertEquals(ItemStatus.AVAILABLE, item.getStatus(), "All items must be restored to AVAILABLE");
            assertTrue(item.isUsable(), "Items must be usable");
        }
    }

    private static void testDamageOnReturnAndAtRisk() {
        KitRelayService service = createTestService();
        LocalDateTime now = LocalDateTime.now();

        // Booking 1: Today 10:00 - 12:00
        Booking b1 = service.bookKit("CLUB-DRAMA", new TimeSlot(now.plusHours(1), now.plusHours(3)), "Drama Rehearsal");

        // Booking 2: Today 14:00 - 16:00 (Upcoming booking)
        Booking b2 = service.bookKit("CLUB-MUSIC", new TimeSlot(now.plusHours(4), now.plusHours(6)), "Music Concert Recording");
        assertEquals(BookingStatus.CONFIRMED, b2.getStatus(), "Upcoming booking initially CONFIRMED");

        // Checkout booking 1
        service.checkoutKit(b1.getBookingId(), "Prof. Miller", "Alex Rivers");

        // Return booking 1 with damaged tripod!
        EventKit kit = service.getEventKit();
        KitItem camera = kit.getItem(ItemType.CAMERA);
        KitItem mic = kit.getItem(ItemType.MICROPHONE);
        KitItem tripod = kit.getItem(ItemType.TRIPOD);

        List<ItemConditionReport> returnReports = List.of(
                new ItemConditionReport(camera.getId(), ItemType.CAMERA, ItemCondition.EXCELLENT, "Camera fine"),
                new ItemConditionReport(mic.getId(), ItemType.MICROPHONE, ItemCondition.EXCELLENT, "Mic fine"),
                new ItemConditionReport(tripod.getId(), ItemType.TRIPOD, ItemCondition.DAMAGED, "Leg locking mechanism cracked during outdoor shoot")
        );

        HandoverRecord returnRecord = service.returnKit(b1.getBookingId(), "Prof. Miller", "Alex Rivers", returnReports, "Returned with cracked tripod leg");

        assertEquals(BookingStatus.COMPLETED, b1.getStatus(), "Booking 1 is completed");

        // Disruption must have been automatically created
        List<Disruption> openDisruptions = service.getOpenDisruptions();
        assertFalse(openDisruptions.isEmpty(), "Open disruption must exist for damaged tripod");
        Disruption d = openDisruptions.get(0);
        assertEquals(DisruptionType.DAMAGED, d.getType(), "Disruption type must be DAMAGED");
        assertEquals(tripod.getId(), d.getItemId(), "Disrupted item must be tripod");

        // Upcoming booking (b2) MUST be marked AT_RISK (Rule 6)
        assertEquals(BookingStatus.AT_RISK, b2.getStatus(), "Upcoming booking must be marked AT_RISK");
        assertTrue(b2.getRiskReason().contains("DAMAGED"), "Risk reason must mention damaged item");
    }

    private static void testDamagedKitBlocksCheckout() {
        KitRelayService service = createTestService();
        LocalDateTime now = LocalDateTime.now();

        Booking booking = service.bookKit("CLUB-ROBOT", new TimeSlot(now.plusHours(1), now.plusHours(3)), "Robot Testing");

        // Manually report a disruption: Microphone is missing
        KitItem mic = service.getEventKit().getItem(ItemType.MICROPHONE);
        service.reportDisruption(mic.getId(), DisruptionType.MISSING, "Misplaced by audio team", null);

        // Inspection must show NOT ready
        InspectionResult inspection = service.inspectKit();
        assertFalse(inspection.isReady(), "Kit must not be ready when mic is missing");

        // Attempting to checkout must throw KitNotAvailableException (Rule 4)
        try {
            service.checkoutKit(booking.getBookingId(), "Prof. Miller", "Devin Vance");
            throw new AssertionError("Should have blocked checkout of incomplete kit");
        } catch (KitNotAvailableException e) {
            assertTrue(e.getMessage().contains("handover rejected"), "Error must state handover rejected");
        }
    }

    private static void testDisruptionResolutionReinstatesBooking() {
        KitRelayService service = createTestService();
        LocalDateTime now = LocalDateTime.now();

        Booking booking = service.bookKit("CLUB-MEDIA", new TimeSlot(now.plusHours(2), now.plusHours(4)), "Campus News Bulletin");

        // Report camera damaged
        KitItem camera = service.getEventKit().getItem(ItemType.CAMERA);
        Disruption disruption = service.reportDisruption(camera.getId(), DisruptionType.DAMAGED, "Shutter error code ERR-20", null);

        assertEquals(BookingStatus.AT_RISK, booking.getStatus(), "Booking must be AT_RISK");

        // Resolve disruption by providing a replacement camera
        KitItem replacementCamera = new KitItem("ITEM-CAM-02", "Canon EOS R6 Mark II", ItemType.CAMERA, "SN-CAN-9912", ItemCondition.EXCELLENT, ItemStatus.AVAILABLE);
        service.resolveDisruption(disruption.getDisruptionId(), "Replaced faulty camera with newly acquired backup Canon EOS R6", replacementCamera);

        // Verify disruption is marked RESOLVED
        Disruption resolvedDisruption = service.getDisruption(disruption.getDisruptionId());
        assertEquals(DisruptionStatus.RESOLVED, resolvedDisruption.getStatus(), "Disruption must be RESOLVED");

        // Verify kit is ready again
        assertTrue(service.inspectKit().isReady(), "Kit must be ready after resolution");

        // Verify booking status was reinstated to CONFIRMED!
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus(), "Booking must be reinstated to CONFIRMED");

        // Should now be able to checkout without errors
        HandoverRecord handover = service.checkoutKit(booking.getBookingId(), "Prof. Miller", "Samira Khan");
        assertEquals(BookingStatus.ACTIVE, booking.getStatus(), "Booking can now proceed to ACTIVE");
    }

    private static void testCancellationWorkflow() {
        KitRelayService service = createTestService();
        LocalDateTime now = LocalDateTime.now();

        Booking booking = service.bookKit("CLUB-DRAMA", new TimeSlot(now.plusHours(1), now.plusHours(3)), "Rehearsal");

        // Cancel booking
        Booking cancelled = service.cancelBooking(booking.getBookingId(), "Club rehearsal postponed due to campus exams");

        assertEquals(BookingStatus.CANCELLED, cancelled.getStatus(), "Booking must be CANCELLED");
        assertEquals("Club rehearsal postponed due to campus exams", cancelled.getCancellationReason(), "Cancellation reason must match");

        // Now that booking is cancelled, its time-slot should be immediately available for another club!
        AvailabilityResult avail = service.checkAvailability(new TimeSlot(now.plusHours(1), now.plusHours(3)));
        assertTrue(avail.isAvailable(), "Time slot of cancelled booking must become available");
    }

    private static void testInvalidStateTransitions() {
        KitRelayService service = createTestService();
        LocalDateTime now = LocalDateTime.now();

        Booking booking = service.bookKit("CLUB-ROBOT", new TimeSlot(now.plusHours(1), now.plusHours(3)), "Tech Fest");
        service.cancelBooking(booking.getBookingId(), "Cancelled");

        // Cannot checkout a cancelled booking
        try {
            service.checkoutKit(booking.getBookingId(), "Prof. Miller", "Devin Vance");
            throw new AssertionError("Cannot checkout cancelled booking");
        } catch (InvalidBookingStateException expected) {
            assertTrue(expected.getMessage().contains("CANCELLED"), "Exception must mention CANCELLED");
        }

        // Cannot return a non-active booking
        Booking booking2 = service.bookKit("CLUB-MUSIC", new TimeSlot(now.plusHours(4), now.plusHours(6)), "Music Rehearsal");
        try {
            service.returnKit(booking2.getBookingId(), "Prof. Miller", "Chloe Bennett", List.of(), "Premature return");
            throw new AssertionError("Cannot return non-active booking");
        } catch (InvalidBookingStateException | IllegalArgumentException expected) {
            // Expected
        }
    }
}
