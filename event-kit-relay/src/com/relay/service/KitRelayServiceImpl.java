package com.relay.service;

import com.relay.dto.AvailabilityResult;
import com.relay.dto.InspectionResult;
import com.relay.exception.*;
import com.relay.model.*;
import com.relay.notification.ConsoleNotificationService;
import com.relay.notification.NotificationService;
import com.relay.repository.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Production-ready implementation of KitRelayService coordinating all booking,
 * inspection, handover, disruption, and resolution workflows.
 */
public class KitRelayServiceImpl implements KitRelayService {

    private final ClubRepository clubRepository;
    private final BookingRepository bookingRepository;
    private final KitRepository kitRepository;
    private final HandoverRepository handoverRepository;
    private final DisruptionRepository disruptionRepository;
    private final NotificationService notificationService;

    private final ReentrantLock lock = new ReentrantLock(true);
    private final AtomicLong bookingSequence = new AtomicLong(100);
    private final AtomicLong handoverSequence = new AtomicLong(200);
    private final AtomicLong disruptionSequence = new AtomicLong(300);

    public KitRelayServiceImpl(ClubRepository clubRepository,
                               BookingRepository bookingRepository,
                               KitRepository kitRepository,
                               HandoverRepository handoverRepository,
                               DisruptionRepository disruptionRepository,
                               NotificationService notificationService) {
        this.clubRepository = Objects.requireNonNull(clubRepository, "ClubRepository cannot be null");
        this.bookingRepository = Objects.requireNonNull(bookingRepository, "BookingRepository cannot be null");
        this.kitRepository = Objects.requireNonNull(kitRepository, "KitRepository cannot be null");
        this.handoverRepository = Objects.requireNonNull(handoverRepository, "HandoverRepository cannot be null");
        this.disruptionRepository = Objects.requireNonNull(disruptionRepository, "DisruptionRepository cannot be null");
        this.notificationService = notificationService != null ? notificationService : new ConsoleNotificationService();
    }

    /**
     * Factory method creating a fully initialized service with standard default kit items.
     */
    public static KitRelayServiceImpl createDefault() {
        KitItem camera = new KitItem("ITEM-CAM-01", "Sony Alpha A7 IV (4K)", ItemType.CAMERA, "SN-CAM-4421", ItemCondition.EXCELLENT, ItemStatus.AVAILABLE);
        KitItem microphone = new KitItem("ITEM-MIC-01", "Røde Wireless PRO Kit", ItemType.MICROPHONE, "SN-MIC-8832", ItemCondition.EXCELLENT, ItemStatus.AVAILABLE);
        KitItem tripod = new KitItem("ITEM-TRP-01", "Manfrotto Befree Live Video Tripod", ItemType.TRIPOD, "SN-TRP-1129", ItemCondition.EXCELLENT, ItemStatus.AVAILABLE);

        EventKit kit = new EventKit("KIT-01", "Central College Media Production Kit");
        kit.registerOrReplaceItem(camera);
        kit.registerOrReplaceItem(microphone);
        kit.registerOrReplaceItem(tripod);

        return new KitRelayServiceImpl(
                new ClubRepository(),
                new BookingRepository(),
                new KitRepository(kit),
                new HandoverRepository(),
                new DisruptionRepository(),
                new ConsoleNotificationService()
        );
    }

    @Override
    public Booking bookKit(String clubId, TimeSlot timeSlot, String eventName) {
        if (clubId == null || clubId.isBlank()) {
            throw new IllegalArgumentException("Club ID cannot be null or empty");
        }
        if (timeSlot == null) {
            throw new IllegalArgumentException("TimeSlot cannot be null");
        }

        lock.lock();
        try {
            Club club = clubRepository.findById(clubId)
                    .orElseThrow(() -> new EntityNotFoundException("Club with ID '" + clubId + "' not registered in system"));

            // Check availability and enforce 15-minute gap
            AvailabilityResult availability = checkAvailability(timeSlot);
            if (!availability.isAvailable()) {
                Booking conflict = availability.getConflictingBooking();
                if (conflict != null && conflict.getTimeSlot().overlapsWith(timeSlot)) {
                    throw new BookingConflictException("Booking rejected: " + availability.getReason());
                } else {
                    throw new InspectionGapViolationException("Booking rejected: " + availability.getReason());
                }
            }

            String bookingId = "BKG-" + bookingSequence.incrementAndGet();
            Booking booking = new Booking(bookingId, club, eventName, timeSlot);

            // Rule 6 check: If there is currently an unresolved disruption, mark new upcoming booking as AT_RISK
            List<Disruption> openDisruptions = disruptionRepository.findOpenDisruptions();
            if (!openDisruptions.isEmpty()) {
                Disruption latest = openDisruptions.get(0);
                booking.markAtRisk("Kit currently disrupted: " + latest.getType() + " (" + latest.getDescription() + ")", latest.getDisruptionId());
                latest.addAffectedBookingId(booking.getBookingId());
                bookingRepository.save(booking);
                notificationService.notifyBookingAtRisk(booking, latest);
            } else {
                bookingRepository.save(booking);
                notificationService.notifyBookingCreated(booking);
            }

            return booking;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public AvailabilityResult checkAvailability(TimeSlot timeSlot) {
        if (timeSlot == null) {
            throw new IllegalArgumentException("TimeSlot cannot be null");
        }

        lock.lock();
        try {
            List<Booking> activeOrUpcoming = bookingRepository.findActiveOrUpcomingBookings();
            for (Booking existing : activeOrUpcoming) {
                // Rule 1: Kit cannot be assigned to overlapping bookings
                if (existing.getTimeSlot().overlapsWith(timeSlot)) {
                    String reason = String.format("Overlaps directly with %s (%s) from Club '%s'",
                            existing.getBookingId(), existing.getTimeSlot(), existing.getClub().getName());
                    return AvailabilityResult.conflict(reason, existing, timeSlot);
                }

                // Rule 2: 15-minute inspection interval must be maintained between consecutive bookings
                if (!existing.getTimeSlot().hasRequiredGap(timeSlot, MANDATORY_INSPECTION_GAP)) {
                    var gap = existing.getTimeSlot().calculateGapBetween(timeSlot);
                    long minutes = (gap != null) ? gap.toMinutes() : 0;
                    String reason = String.format("Violates mandatory 15-minute inspection interval. Current interval with %s (%s) is %d minutes.",
                            existing.getBookingId(), existing.getTimeSlot(), minutes);
                    return AvailabilityResult.conflict(reason, existing, timeSlot);
                }
            }
            return AvailabilityResult.available(timeSlot);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public HandoverRecord checkoutKit(String bookingId, String organizerName, String clubRepresentative) {
        if (bookingId == null || bookingId.isBlank()) throw new IllegalArgumentException("Booking ID cannot be blank");
        if (organizerName == null || organizerName.isBlank()) throw new IllegalArgumentException("Organizer name cannot be blank");
        if (clubRepresentative == null || clubRepresentative.isBlank()) throw new IllegalArgumentException("Club representative cannot be blank");

        lock.lock();
        try {
            Booking booking = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new EntityNotFoundException("Booking '" + bookingId + "' not found"));

            if (booking.getStatus() == BookingStatus.ACTIVE) {
                throw new InvalidBookingStateException("Kit is already checked out for booking " + bookingId);
            }
            if (booking.getStatus() == BookingStatus.COMPLETED) {
                throw new InvalidBookingStateException("Cannot checkout: Booking " + bookingId + " has already been COMPLETED");
            }
            if (booking.getStatus() == BookingStatus.CANCELLED) {
                throw new InvalidBookingStateException("Cannot checkout: Booking " + bookingId + " was CANCELLED (" + booking.getCancellationReason() + ")");
            }

            // Rule 4: The kit can be issued only when all required items are available and usable.
            InspectionResult inspection = inspectKit();
            if (!inspection.isReady()) {
                throw new KitNotAvailableException("Kit handover rejected. Equipment is not complete and usable: " + inspection.getIssues());
            }

            EventKit kit = kitRepository.getKit();
            List<ItemConditionReport> itemSnapshots = new ArrayList<>();
            for (KitItem item : kit.getAllItems()) {
                item.markIssued();
                itemSnapshots.add(new ItemConditionReport(
                        item.getId(),
                        item.getType(),
                        item.getCondition(),
                        "Verified usable and issued to " + clubRepresentative
                ));
            }

            // Rule 3: Every checkout and return must be documented through a handover record.
            String recordId = "HO-OUT-" + handoverSequence.incrementAndGet();
            HandoverRecord record = new HandoverRecord(
                    recordId,
                    bookingId,
                    HandoverType.CHECKOUT,
                    organizerName,
                    clubRepresentative,
                    itemSnapshots,
                    "Checkout verified by organizer. Event: " + booking.getEventName()
            );

            handoverRepository.save(record);
            booking.markActive();
            bookingRepository.save(booking);

            return record;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public HandoverRecord returnKit(String bookingId, String organizerName, String clubRepresentative,
                                    List<ItemConditionReport> returnReports, String remarks) {
        if (bookingId == null || bookingId.isBlank()) throw new IllegalArgumentException("Booking ID cannot be blank");
        if (organizerName == null || organizerName.isBlank()) throw new IllegalArgumentException("Organizer name cannot be blank");
        if (clubRepresentative == null || clubRepresentative.isBlank()) throw new IllegalArgumentException("Club representative cannot be blank");
        if (returnReports == null || returnReports.isEmpty()) {
            throw new IllegalArgumentException("Return reports must include condition for returned equipment");
        }

        lock.lock();
        try {
            Booking booking = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new EntityNotFoundException("Booking '" + bookingId + "' not found"));

            if (booking.getStatus() != BookingStatus.ACTIVE) {
                throw new InvalidBookingStateException("Cannot process return: Booking " + bookingId + " is in status " + booking.getStatus() + ", expected ACTIVE.");
            }

            EventKit kit = kitRepository.getKit();
            List<ItemConditionReport> recordedSnapshots = new ArrayList<>();

            for (ItemConditionReport report : returnReports) {
                KitItem item = kit.getItemById(report.getItemId())
                        .orElseThrow(() -> new EntityNotFoundException("Returned item '" + report.getItemId() + "' does not belong to kit"));

                recordedSnapshots.add(report);

                // Update item status & condition
                if (report.getCondition().isUsable()) {
                    item.updateCondition(report.getCondition(), ItemStatus.AVAILABLE, report.getRemarks());
                } else {
                    // Rule 5: Missing or damaged equipment must be recorded as a disruption.
                    DisruptionType dType = report.getCondition() == ItemCondition.MISSING ?
                            DisruptionType.MISSING : DisruptionType.DAMAGED;
                    item.updateCondition(report.getCondition(),
                            dType == DisruptionType.MISSING ? ItemStatus.MISSING : ItemStatus.DAMAGED,
                            report.getRemarks());

                    // Automatically file disruption record
                    reportDisruption(item.getId(), dType,
                            "Reported on return of booking " + bookingId + ": " + report.getRemarks(),
                            bookingId);
                }
            }

            // Rule 3: Every checkout and return must be documented through a handover record.
            String recordId = "HO-RET-" + handoverSequence.incrementAndGet();
            HandoverRecord record = new HandoverRecord(
                    recordId,
                    bookingId,
                    HandoverType.RETURN,
                    organizerName,
                    clubRepresentative,
                    recordedSnapshots,
                    remarks != null ? remarks : "Return inspection completed."
            );

            handoverRepository.save(record);
            booking.markCompleted();
            bookingRepository.save(booking);

            return record;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public InspectionResult inspectKit() {
        lock.lock();
        try {
            EventKit kit = kitRepository.getKit();
            boolean ready = kit.isCompleteAndReady();
            List<String> issues = kit.getDefectsOrMissingReasons();
            return new InspectionResult(ready, kit, issues);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Disruption reportDisruption(String itemId, DisruptionType type, String description, String originatingBookingId) {
        if (itemId == null || itemId.isBlank()) throw new IllegalArgumentException("Item ID cannot be blank");
        if (type == null) throw new IllegalArgumentException("DisruptionType cannot be null");

        lock.lock();
        try {
            EventKit kit = kitRepository.getKit();
            KitItem item = kit.getItemById(itemId)
                    .orElseThrow(() -> new EntityNotFoundException("Item with ID '" + itemId + "' not found in kit"));

            if (type == DisruptionType.MISSING) {
                item.markMissing(description);
            } else {
                item.markDamaged(description);
            }

            String disruptionId = "DIS-" + disruptionSequence.incrementAndGet();
            Disruption disruption = new Disruption(disruptionId, item.getId(), item.getType(), type, description, originatingBookingId);

            // Rule 6: Upcoming bookings affected by a disruption must be marked as at risk.
            List<Booking> upcoming = bookingRepository.findUpcomingFrom(LocalDateTime.now());
            for (Booking upcomingBooking : upcoming) {
                // Do not mark already completed or cancelled bookings
                if (upcomingBooking.getStatus() == BookingStatus.CONFIRMED || upcomingBooking.getStatus() == BookingStatus.REQUESTED) {
                    String reason = String.format("Required item %s (%s) is %s [%s]",
                            item.getName(), item.getType().getDisplayName(), type, description);
                    upcomingBooking.markAtRisk(reason, disruptionId);
                    disruption.addAffectedBookingId(upcomingBooking.getBookingId());
                    bookingRepository.save(upcomingBooking);

                    // Notify affected club
                    notificationService.notifyBookingAtRisk(upcomingBooking, disruption);
                }
            }

            disruptionRepository.save(disruption);
            notificationService.notifyDisruptionLogged(disruption);

            return disruption;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void resolveDisruption(String disruptionId, String resolutionDetails, KitItem replacementItem) {
        if (disruptionId == null || disruptionId.isBlank()) throw new IllegalArgumentException("Disruption ID cannot be blank");

        lock.lock();
        try {
            Disruption disruption = disruptionRepository.findById(disruptionId)
                    .orElseThrow(() -> new EntityNotFoundException("Disruption '" + disruptionId + "' not found"));

            if (!disruption.isOpen()) {
                throw new KitRelayException("Disruption " + disruptionId + " has already been resolved.");
            }

            EventKit kit = kitRepository.getKit();

            if (replacementItem != null) {
                if (replacementItem.getType() != disruption.getItemType()) {
                    throw new IllegalArgumentException(String.format("Replacement item type mismatch: expected %s but got %s",
                            disruption.getItemType(), replacementItem.getType()));
                }
                kit.registerOrReplaceItem(replacementItem);
                disruption.resolve(resolutionDetails, replacementItem.getId());
            } else {
                // Item was recovered, repaired, or serviced
                KitItem item = kit.getItemById(disruption.getItemId())
                        .orElseThrow(() -> new EntityNotFoundException("Item " + disruption.getItemId() + " not found"));
                item.markRepairedOrReplaced(ItemCondition.EXCELLENT, item.getSerialNumber(), resolutionDetails);
                disruption.resolve(resolutionDetails, null);
            }

            disruptionRepository.save(disruption);

            // If all open disruptions are resolved and kit is ready, reinstate at-risk bookings
            if (kit.isCompleteAndReady() && disruptionRepository.findOpenDisruptions().isEmpty()) {
                for (String bookingId : disruption.getAffectedBookingIds()) {
                    bookingRepository.findById(bookingId).ifPresent(b -> {
                        if (b.getStatus() == BookingStatus.AT_RISK) {
                            b.reinstateConfirmed();
                            bookingRepository.save(b);
                            notificationService.notifyBookingReconfirmed(b, resolutionDetails);
                        }
                    });
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Booking cancelBooking(String bookingId, String reason) {
        if (bookingId == null || bookingId.isBlank()) throw new IllegalArgumentException("Booking ID cannot be blank");

        lock.lock();
        try {
            Booking booking = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new EntityNotFoundException("Booking '" + bookingId + "' not found"));

            if (booking.getStatus() == BookingStatus.COMPLETED) {
                throw new InvalidBookingStateException("Cannot cancel completed booking " + bookingId);
            }
            if (booking.getStatus() == BookingStatus.CANCELLED) {
                throw new InvalidBookingStateException("Booking " + bookingId + " is already cancelled");
            }

            booking.markCancelled(reason != null ? reason : "Cancelled by coordinator");
            bookingRepository.save(booking);

            notificationService.notifyBookingCancelled(booking, reason);

            return booking;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void registerClub(Club club) {
        clubRepository.save(club);
    }

    @Override
    public Club getClub(String clubId) {
        return clubRepository.findById(clubId).orElse(null);
    }

    @Override
    public List<Club> getAllClubs() {
        return clubRepository.findAll();
    }

    @Override
    public Booking getBooking(String bookingId) {
        return bookingRepository.findById(bookingId).orElse(null);
    }

    @Override
    public List<Booking> getAllBookings() {
        return bookingRepository.findAll();
    }

    @Override
    public List<Booking> getActiveOrUpcomingBookings() {
        return bookingRepository.findActiveOrUpcomingBookings();
    }

    @Override
    public EventKit getEventKit() {
        return kitRepository.getKit();
    }

    @Override
    public Disruption getDisruption(String disruptionId) {
        return disruptionRepository.findById(disruptionId).orElse(null);
    }

    @Override
    public List<Disruption> getAllDisruptions() {
        return disruptionRepository.findAll();
    }

    @Override
    public List<Disruption> getOpenDisruptions() {
        return disruptionRepository.findOpenDisruptions();
    }

    @Override
    public List<HandoverRecord> getHandoverRecords(String bookingId) {
        return handoverRepository.findByBookingId(bookingId);
    }

    @Override
    public List<HandoverRecord> getAllHandoverRecords() {
        return handoverRepository.findAll();
    }
}
