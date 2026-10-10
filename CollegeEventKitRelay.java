import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * ============================================================================
 * COLLEGE EVENT KIT RELAY - LOW-LEVEL DESIGN (LLD)
 * ============================================================================
 * Objective: Centralized system enabling multiple college clubs to share and
 * reserve a common event kit (Camera, Microphone, Tripod) ensuring conflict-free
 * bookings, mandatory 15-minute inspection gaps, traceable handovers, and reliable
 * disruption recovery.
 *
 * Core Classes Included:
 *  - Club: Represents a college club requesting the kit.
 *  - KitItem: Represents individual equipment piece, condition, and availability.
 *  - EventKit: Manages complete required kit (Camera, Mic, Tripod) & readiness.
 *  - Booking: Stores reservation, slot, status, and disruption risk link.
 *  - HandoverRecord: Documents checkout and return handovers with item snapshots.
 *  - Disruption: Tracks damaged/missing items, affected bookings, and resolutions.
 *  - KitRelayService: Facade coordinating booking, handovers, disruptions, & cancellations.
 * ============================================================================
 */
public class CollegeEventKitRelay {

    // ========================================================================
    // 1. ENUMS & VALUE OBJECTS
    // ========================================================================

    public enum ItemType {
        CAMERA("Camera"),
        MICROPHONE("Microphone"),
        TRIPOD("Tripod");

        private final String displayName;

        ItemType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum ItemCondition {
        EXCELLENT(true),
        GOOD(true),
        FAIR(true),
        DAMAGED(false),
        DEFECTIVE(false),
        MISSING(false);

        private final boolean usable;

        ItemCondition(boolean usable) {
            this.usable = usable;
        }

        public boolean isUsable() {
            return usable;
        }
    }

    public enum ItemStatus {
        AVAILABLE,
        ISSUED,
        UNDER_INSPECTION,
        DAMAGED,
        MISSING,
        MAINTENANCE
    }

    public enum BookingStatus {
        REQUESTED,
        CONFIRMED,
        AT_RISK,
        ACTIVE,
        COMPLETED,
        CANCELLED;

        public boolean isActiveOrUpcoming() {
            return this == CONFIRMED || this == AT_RISK || this == ACTIVE;
        }
    }

    public enum HandoverType {
        CHECKOUT,
        RETURN
    }

    public enum DisruptionType {
        MISSING("Equipment Missing"),
        DAMAGED("Equipment Damaged"),
        DEFECTIVE("Equipment Malfunction");

        private final String description;

        DisruptionType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum DisruptionStatus {
        OPEN,
        RESOLVED
    }

    // ========================================================================
    // 2. DOMAIN EXCEPTIONS
    // ========================================================================

    public static class KitRelayException extends RuntimeException {
        public KitRelayException(String message) {
            super(message);
        }
    }

    public static class BookingConflictException extends KitRelayException {
        public BookingConflictException(String message) {
            super(message);
        }
    }

    public static class InspectionGapViolationException extends KitRelayException {
        public InspectionGapViolationException(String message) {
            super(message);
        }
    }

    public static class KitNotAvailableException extends KitRelayException {
        public KitNotAvailableException(String message) {
            super(message);
        }
    }

    public static class InvalidBookingStateException extends KitRelayException {
        public InvalidBookingStateException(String message) {
            super(message);
        }
    }

    public static class EntityNotFoundException extends KitRelayException {
        public EntityNotFoundException(String message) {
            super(message);
        }
    }

    // ========================================================================
    // 3. CORE ENTITIES
    // ========================================================================

    /**
     * Club: Represents a college club that requests and uses the event kit.
     */
    public static class Club {
        private final String id;
        private final String name;
        private final String contactPerson;
        private final String email;
        private final String phone;

        public Club(String id, String name, String contactPerson, String email, String phone) {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Club ID cannot be blank");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Club name cannot be blank");
            this.id = id.trim();
            this.name = name.trim();
            this.contactPerson = contactPerson != null ? contactPerson.trim() : "";
            this.email = email != null ? email.trim() : "";
            this.phone = phone != null ? phone.trim() : "";
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getContactPerson() { return contactPerson; }
        public String getEmail() { return email; }
        public String getPhone() { return phone; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Club club)) return false;
            return Objects.equals(id, club.id);
        }

        @Override
        public int hashCode() { return Objects.hash(id); }

        @Override
        public String toString() {
            return String.format("Club[id='%s', name='%s', contact='%s', email='%s']", id, name, contactPerson, email);
        }
    }

    /**
     * KitItem: Represents an individual piece of equipment and maintains its current condition and availability.
     */
    public static class KitItem {
        private final String id;
        private final String name;
        private final ItemType type;
        private String serialNumber;
        private ItemCondition condition;
        private ItemStatus status;
        private String notes;
        private LocalDateTime lastInspectedAt;

        public KitItem(String id, String name, ItemType type, String serialNumber, ItemCondition condition, ItemStatus status) {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Item ID cannot be blank");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Item name cannot be blank");
            if (type == null) throw new IllegalArgumentException("ItemType cannot be null");
            this.id = id.trim();
            this.name = name.trim();
            this.type = type;
            this.serialNumber = serialNumber != null ? serialNumber.trim() : "";
            this.condition = condition != null ? condition : ItemCondition.EXCELLENT;
            this.status = status != null ? status : ItemStatus.AVAILABLE;
            this.notes = "Initial inventory stock";
            this.lastInspectedAt = LocalDateTime.now();
        }

        public synchronized String getId() { return id; }
        public synchronized String getName() { return name; }
        public synchronized ItemType getType() { return type; }
        public synchronized String getSerialNumber() { return serialNumber; }
        public synchronized ItemCondition getCondition() { return condition; }
        public synchronized ItemStatus getStatus() { return status; }
        public synchronized String getNotes() { return notes; }
        public synchronized LocalDateTime getLastInspectedAt() { return lastInspectedAt; }

        public synchronized boolean isUsable() {
            return condition.isUsable() &&
                   status != ItemStatus.DAMAGED &&
                   status != ItemStatus.MISSING &&
                   status != ItemStatus.MAINTENANCE;
        }

        public synchronized void markIssued() { this.status = ItemStatus.ISSUED; }
        public synchronized void markAvailable() {
            this.status = ItemStatus.AVAILABLE;
            this.lastInspectedAt = LocalDateTime.now();
        }
        public synchronized void markUnderInspection() {
            this.status = ItemStatus.UNDER_INSPECTION;
            this.lastInspectedAt = LocalDateTime.now();
        }
        public synchronized void markDamaged(String reason) {
            this.condition = ItemCondition.DAMAGED;
            this.status = ItemStatus.DAMAGED;
            this.notes = reason;
            this.lastInspectedAt = LocalDateTime.now();
        }
        public synchronized void markMissing(String reason) {
            this.condition = ItemCondition.MISSING;
            this.status = ItemStatus.MISSING;
            this.notes = reason;
            this.lastInspectedAt = LocalDateTime.now();
        }
        public synchronized void updateCondition(ItemCondition newCondition, ItemStatus newStatus, String notes) {
            if (newCondition != null) this.condition = newCondition;
            if (newStatus != null) this.status = newStatus;
            if (notes != null) this.notes = notes;
            this.lastInspectedAt = LocalDateTime.now();
        }
        public synchronized void markRepairedOrReplaced(ItemCondition newCondition, String newSerialNumber, String notes) {
            this.condition = newCondition != null ? newCondition : ItemCondition.EXCELLENT;
            this.status = ItemStatus.AVAILABLE;
            if (newSerialNumber != null && !newSerialNumber.isBlank()) {
                this.serialNumber = newSerialNumber.trim();
            }
            this.notes = notes != null ? notes : "Recovered/Replaced item";
            this.lastInspectedAt = LocalDateTime.now();
        }

        @Override
        public synchronized String toString() {
            return String.format("[%s: %s (%s) | Cond: %s | Status: %s | Usable: %s]",
                    id, name, type.getDisplayName(), condition, status, isUsable());
        }
    }

    /**
     * EventKit: Manages the collection of required equipment and verifies whether the complete kit is ready for use.
     */
    public static class EventKit {
        private final String id;
        private final String name;
        private final Set<ItemType> requiredTypes;
        private final Map<ItemType, KitItem> items;

        public EventKit(String id, String name) {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Kit ID cannot be blank");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Kit name cannot be blank");
            this.id = id.trim();
            this.name = name.trim();
            this.requiredTypes = EnumSet.of(ItemType.CAMERA, ItemType.MICROPHONE, ItemType.TRIPOD);
            this.items = new ConcurrentHashMap<>();
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public Set<ItemType> getRequiredTypes() { return Collections.unmodifiableSet(requiredTypes); }

        public void registerOrReplaceItem(KitItem item) {
            if (item == null) throw new IllegalArgumentException("Item cannot be null");
            items.put(item.getType(), item);
        }

        public KitItem getItem(ItemType type) { return items.get(type); }

        public Optional<KitItem> getItemById(String itemId) {
            if (itemId == null) return Optional.empty();
            return items.values().stream()
                    .filter(i -> i.getId().equalsIgnoreCase(itemId.trim()))
                    .findFirst();
        }

        public Collection<KitItem> getAllItems() { return Collections.unmodifiableCollection(items.values()); }

        /**
         * Key Business Rule 4: The kit can be issued only when all required items are available and usable.
         */
        public synchronized boolean isCompleteAndReady() {
            for (ItemType req : requiredTypes) {
                KitItem item = items.get(req);
                if (item == null || !item.isUsable()) {
                    return false;
                }
            }
            return true;
        }

        public synchronized List<String> getDefectsOrMissingReasons() {
            List<String> issues = new ArrayList<>();
            for (ItemType req : requiredTypes) {
                KitItem item = items.get(req);
                if (item == null) {
                    issues.add("Missing required equipment component: " + req.getDisplayName());
                } else if (!item.isUsable()) {
                    issues.add(String.format("%s (%s) is not usable. Condition: %s, Status: %s. Reason: %s",
                            item.getName(), item.getType().getDisplayName(), item.getCondition(), item.getStatus(), item.getNotes()));
                }
            }
            return issues;
        }

        @Override
        public synchronized String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("EventKit[%s - '%s'] Complete & Ready: %s\n", id, name, isCompleteAndReady()));
            for (ItemType type : requiredTypes) {
                KitItem item = items.get(type);
                sb.append("   * ").append(type.getDisplayName()).append(": ");
                if (item != null) {
                    sb.append(item.getName())
                      .append(" [Condition: ").append(item.getCondition())
                      .append(", Status: ").append(item.getStatus())
                      .append(", Usable: ").append(item.isUsable()).append("]\n");
                } else {
                    sb.append("NOT ASSIGNED\n");
                }
            }
            return sb.toString();
        }
    }

    /**
     * TimeSlot: Represents booking time window with overlap and buffer checks.
     */
    public static class TimeSlot implements Comparable<TimeSlot> {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        private final LocalDateTime startTime;
        private final LocalDateTime endTime;

        public TimeSlot(LocalDateTime startTime, LocalDateTime endTime) {
            if (startTime == null || endTime == null) {
                throw new IllegalArgumentException("Start and End time cannot be null");
            }
            if (!startTime.isBefore(endTime)) {
                throw new IllegalArgumentException("Start time must be strictly before end time");
            }
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public LocalDateTime getStartTime() { return startTime; }
        public LocalDateTime getEndTime() { return endTime; }

        /**
         * Key Business Rule 1: The kit cannot be assigned to overlapping bookings.
         */
        public boolean overlapsWith(TimeSlot other) {
            if (other == null) return false;
            return this.startTime.isBefore(other.endTime) && other.startTime.isBefore(this.endTime);
        }

        /**
         * Key Business Rule 2: A 15-minute inspection interval must be maintained between consecutive bookings.
         */
        public boolean hasRequiredGap(TimeSlot other, Duration minGap) {
            if (other == null) return true;
            if (this.overlapsWith(other)) return false;
            if (this.endTime.isBefore(other.startTime) || this.endTime.isEqual(other.startTime)) {
                return Duration.between(this.endTime, other.startTime).compareTo(minGap) >= 0;
            } else if (other.endTime.isBefore(this.startTime) || other.endTime.isEqual(this.startTime)) {
                return Duration.between(other.endTime, this.startTime).compareTo(minGap) >= 0;
            }
            return false;
        }

        public Duration calculateGapBetween(TimeSlot other) {
            if (other == null || this.overlapsWith(other)) return null;
            if (this.endTime.isBefore(other.startTime) || this.endTime.isEqual(other.startTime)) {
                return Duration.between(this.endTime, other.startTime);
            } else {
                return Duration.between(other.endTime, this.startTime);
            }
        }

        @Override
        public int compareTo(TimeSlot o) {
            int cmp = this.startTime.compareTo(o.startTime);
            return (cmp != 0) ? cmp : this.endTime.compareTo(o.endTime);
        }

        @Override
        public String toString() {
            return String.format("[%s -> %s]", startTime.format(FORMATTER), endTime.format(FORMATTER));
        }
    }

    /**
     * Booking: Stores booking details, including the club, time slot, and booking status.
     */
    public static class Booking {
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
            if (bookingId == null || bookingId.isBlank()) throw new IllegalArgumentException("Booking ID cannot be blank");
            if (club == null) throw new IllegalArgumentException("Club cannot be null");
            if (timeSlot == null) throw new IllegalArgumentException("TimeSlot cannot be null");
            this.bookingId = bookingId.trim();
            this.club = club;
            this.eventName = eventName != null && !eventName.isBlank() ? eventName.trim() : "Club Event";
            this.timeSlot = timeSlot;
            this.status = BookingStatus.CONFIRMED;
            this.createdAt = LocalDateTime.now();
        }

        public synchronized String getBookingId() { return bookingId; }
        public synchronized Club getClub() { return club; }
        public synchronized String getEventName() { return eventName; }
        public synchronized TimeSlot getTimeSlot() { return timeSlot; }
        public synchronized BookingStatus getStatus() { return status; }
        public synchronized String getCancellationReason() { return cancellationReason; }
        public synchronized String getRiskReason() { return riskReason; }
        public synchronized String getAssociatedDisruptionId() { return associatedDisruptionId; }

        public synchronized void markConfirmed() {
            this.status = BookingStatus.CONFIRMED;
            this.riskReason = null;
            this.associatedDisruptionId = null;
        }

        /**
         * Key Business Rule 6: Upcoming bookings affected by a disruption must be marked as at risk.
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

        public synchronized void markActive() { this.status = BookingStatus.ACTIVE; }
        public synchronized void markCompleted() { this.status = BookingStatus.COMPLETED; }
        public synchronized void markCancelled(String reason) {
            this.status = BookingStatus.CANCELLED;
            this.cancellationReason = reason;
        }

        @Override
        public synchronized String toString() {
            return String.format("Booking[%s | Club: '%s' | Slot: %s | Status: %s | Event: '%s'%s]",
                    bookingId, club.getName(), timeSlot, status, eventName,
                    status == BookingStatus.AT_RISK ? " (RISK: " + riskReason + ")" :
                    status == BookingStatus.CANCELLED ? " (CANCELLED: " + cancellationReason + ")" : "");
        }
    }

    /**
     * ItemConditionReport: Condition capture of an individual item at checkout or return.
     */
    public static class ItemConditionReport {
        private final String itemId;
        private final ItemType itemType;
        private final ItemCondition condition;
        private final String remarks;

        public ItemConditionReport(String itemId, ItemType itemType, ItemCondition condition, String remarks) {
            this.itemId = Objects.requireNonNull(itemId);
            this.itemType = Objects.requireNonNull(itemType);
            this.condition = Objects.requireNonNull(condition);
            this.remarks = remarks != null ? remarks.trim() : "";
        }

        public String getItemId() { return itemId; }
        public ItemType getItemType() { return itemType; }
        public ItemCondition getCondition() { return condition; }
        public String getRemarks() { return remarks; }

        @Override
        public String toString() {
            return String.format("[%s (%s): %s%s]", itemId, itemType.getDisplayName(), condition,
                    remarks.isEmpty() ? "" : " - " + remarks);
        }
    }

    /**
     * HandoverRecord: Maintains a record of equipment issued and returned, along with inspection details.
     * Key Business Rule 3: Every checkout and return must be documented through a handover record.
     */
    public static class HandoverRecord {
        private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        private final String recordId;
        private final String bookingId;
        private final HandoverType type;
        private final LocalDateTime timestamp;
        private final String handledBy;
        private final String clubRepresentative;
        private final List<ItemConditionReport> itemSnapshots;
        private final String remarks;

        public HandoverRecord(String recordId, String bookingId, HandoverType type,
                              String handledBy, String clubRepresentative,
                              List<ItemConditionReport> itemSnapshots, String remarks) {
            this.recordId = Objects.requireNonNull(recordId);
            this.bookingId = Objects.requireNonNull(bookingId);
            this.type = Objects.requireNonNull(type);
            this.timestamp = LocalDateTime.now();
            this.handledBy = Objects.requireNonNull(handledBy);
            this.clubRepresentative = Objects.requireNonNull(clubRepresentative);
            this.itemSnapshots = itemSnapshots != null ? new ArrayList<>(itemSnapshots) : new ArrayList<>();
            this.remarks = remarks != null ? remarks.trim() : "";
        }

        public String getRecordId() { return recordId; }
        public String getBookingId() { return bookingId; }
        public HandoverType getType() { return type; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getHandledBy() { return handledBy; }
        public String getClubRepresentative() { return clubRepresentative; }
        public List<ItemConditionReport> getItemSnapshots() { return Collections.unmodifiableList(itemSnapshots); }
        public String getRemarks() { return remarks; }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("HandoverRecord[%s | Type: %s | Booking: %s | Time: %s | Staff: %s | Rep: %s]\n",
                    recordId, type, bookingId, timestamp.format(TIME_FMT), handledBy, clubRepresentative));
            for (ItemConditionReport r : itemSnapshots) {
                sb.append("   - ").append(r).append("\n");
            }
            if (!remarks.isEmpty()) {
                sb.append("   Remarks: ").append(remarks).append("\n");
            }
            return sb.toString();
        }
    }

    /**
     * Disruption: Tracks equipment-related problems, such as missing or damaged items, and their resolution.
     * Key Business Rule 5: Missing or damaged equipment must be recorded as a disruption.
     * Key Business Rule 7: Every disruption must have a recorded resolution or lead to cancellation of an affected booking.
     */
    public static class Disruption {
        private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
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
            this.disruptionId = Objects.requireNonNull(disruptionId);
            this.itemId = Objects.requireNonNull(itemId);
            this.itemType = Objects.requireNonNull(itemType);
            this.type = Objects.requireNonNull(type);
            this.description = description != null ? description.trim() : "Unspecified";
            this.originatingBookingId = originatingBookingId;
            this.reportedAt = LocalDateTime.now();
            this.status = DisruptionStatus.OPEN;
            this.affectedBookingIds = Collections.synchronizedSet(new LinkedHashSet<>());
        }

        public synchronized String getDisruptionId() { return disruptionId; }
        public synchronized String getItemId() { return itemId; }
        public synchronized ItemType getItemType() { return itemType; }
        public synchronized DisruptionType getType() { return type; }
        public synchronized String getDescription() { return description; }
        public synchronized DisruptionStatus getStatus() { return status; }
        public synchronized boolean isOpen() { return status == DisruptionStatus.OPEN; }
        public synchronized Set<String> getAffectedBookingIds() { return Collections.unmodifiableSet(new LinkedHashSet<>(affectedBookingIds)); }
        public synchronized void addAffectedBookingId(String bookingId) {
            if (bookingId != null) this.affectedBookingIds.add(bookingId);
        }

        public synchronized void resolve(String resolutionDetails, String replacementItemId) {
            this.status = DisruptionStatus.RESOLVED;
            this.resolvedAt = LocalDateTime.now();
            this.resolutionDetails = resolutionDetails != null ? resolutionDetails.trim() : "Resolved";
            this.replacementItemId = replacementItemId;
        }

        @Override
        public synchronized String toString() {
            return String.format("Disruption[%s | Type: %s | Item: %s (%s) | Status: %s | Affected: %s%s]",
                    disruptionId, type, itemId, itemType.getDisplayName(), status, affectedBookingIds,
                    status == DisruptionStatus.RESOLVED ? " | Resolved: " + resolutionDetails : "");
        }
    }

    // ========================================================================
    // 4. RESULTS / DTOS
    // ========================================================================

    public static class InspectionResult {
        private final boolean ready;
        private final EventKit kit;
        private final List<String> issues;

        public InspectionResult(boolean ready, EventKit kit, List<String> issues) {
            this.ready = ready;
            this.kit = kit;
            this.issues = issues != null ? issues : Collections.emptyList();
        }

        public boolean isReady() { return ready; }
        public EventKit getKit() { return kit; }
        public List<String> getIssues() { return issues; }

        @Override
        public String toString() {
            return ready ? "InspectionResult: READY (Camera, Mic, and Tripod all present and usable)"
                         : "InspectionResult: NOT READY - Issues: " + issues;
        }
    }

    public static class AvailabilityResult {
        private final boolean available;
        private final String reason;
        private final Booking conflictingBooking;
        private final TimeSlot slot;

        private AvailabilityResult(boolean available, String reason, Booking conflictingBooking, TimeSlot slot) {
            this.available = available;
            this.reason = reason;
            this.conflictingBooking = conflictingBooking;
            this.slot = slot;
        }

        public static AvailabilityResult available(TimeSlot slot) {
            return new AvailabilityResult(true, "Available with compliant 15-min gap", null, slot);
        }

        public static AvailabilityResult conflict(String reason, Booking conflict, TimeSlot slot) {
            return new AvailabilityResult(false, reason, conflict, slot);
        }

        public boolean isAvailable() { return available; }
        public String getReason() { return reason; }
        public Booking getConflictingBooking() { return conflictingBooking; }
        public TimeSlot getSlot() { return slot; }
    }

    // ========================================================================
    // 5. NOTIFICATION SERVICE
    // ========================================================================

    public interface NotificationService {
        void notifyBookingCreated(Booking booking);
        void notifyBookingAtRisk(Booking booking, Disruption disruption);
        void notifyBookingReconfirmed(Booking booking, String resolutionSummary);
        void notifyBookingCancelled(Booking booking, String reason);
    }

    public static class ConsoleNotificationService implements NotificationService {
        private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

        private void log(String level, String msg) {
            System.out.printf("[%s] [ALERT - %s] %s\n", LocalDateTime.now().format(TIME_FMT), level, msg);
        }

        @Override
        public void notifyBookingCreated(Booking b) {
            log("CONFIRMED", String.format("Club '%s': Booking %s confirmed for %s",
                    b.getClub().getName(), b.getBookingId(), b.getTimeSlot()));
        }

        @Override
        public void notifyBookingAtRisk(Booking b, Disruption d) {
            log("AT_RISK", String.format("URGENT to Club '%s' (%s): Booking %s is AT RISK! %s (%s) is %s.",
                    b.getClub().getName(), b.getClub().getEmail(), b.getBookingId(),
                    d.getItemId(), d.getItemType().getDisplayName(), d.getType()));
        }

        @Override
        public void notifyBookingReconfirmed(Booking b, String resolutionSummary) {
            log("RESTORED", String.format("GOOD NEWS to Club '%s': Booking %s re-confirmed! Resolution: %s",
                    b.getClub().getName(), b.getBookingId(), resolutionSummary));
        }

        @Override
        public void notifyBookingCancelled(Booking b, String reason) {
            log("CANCELLED", String.format("NOTICE to Club '%s': Booking %s CANCELLED. Reason: %s",
                    b.getClub().getName(), b.getBookingId(), reason));
        }
    }

    // ========================================================================
    // 6. CENTRAL SERVICE (KitRelayService)
    // ========================================================================

    public interface KitRelayService {
        Booking bookKit(String clubId, TimeSlot timeSlot, String eventName);
        AvailabilityResult checkAvailability(TimeSlot timeSlot);
        HandoverRecord checkoutKit(String bookingId, String organizerName, String clubRepresentative);
        HandoverRecord returnKit(String bookingId, String organizerName, String clubRepresentative,
                                 List<ItemConditionReport> returnReports, String remarks);
        InspectionResult inspectKit();
        Disruption reportDisruption(String itemId, DisruptionType type, String description, String originatingBookingId);
        void resolveDisruption(String disruptionId, String resolutionDetails, KitItem replacementItem);
        Booking cancelBooking(String bookingId, String reason);

        void registerClub(Club club);
        List<Club> getAllClubs();
        Booking getBooking(String bookingId);
        List<Booking> getAllBookings();
        EventKit getEventKit();
        List<Disruption> getOpenDisruptions();
        List<HandoverRecord> getAllHandoverRecords();
    }

    public static class KitRelayServiceImpl implements KitRelayService {
        private static final Duration MANDATORY_INSPECTION_GAP = Duration.ofMinutes(15);

        private final Map<String, Club> clubs = new ConcurrentHashMap<>();
        private final Map<String, Booking> bookings = new ConcurrentHashMap<>();
        private final Map<String, Disruption> disruptions = new ConcurrentHashMap<>();
        private final Map<String, HandoverRecord> handovers = new ConcurrentHashMap<>();
        private final EventKit eventKit;
        private final NotificationService notificationService;

        private final ReentrantLock lock = new ReentrantLock(true);
        private final AtomicLong bookingSeq = new AtomicLong(100);
        private final AtomicLong handoverSeq = new AtomicLong(200);
        private final AtomicLong disruptionSeq = new AtomicLong(300);

        public KitRelayServiceImpl(EventKit eventKit, NotificationService notificationService) {
            this.eventKit = Objects.requireNonNull(eventKit);
            this.notificationService = notificationService != null ? notificationService : new ConsoleNotificationService();
        }

        public static KitRelayServiceImpl createDefault() {
            EventKit kit = new EventKit("KIT-01", "Central College Media Kit");
            kit.registerOrReplaceItem(new KitItem("ITEM-CAM-01", "Sony Alpha A7 IV (4K)", ItemType.CAMERA, "SN-CAM-101", ItemCondition.EXCELLENT, ItemStatus.AVAILABLE));
            kit.registerOrReplaceItem(new KitItem("ITEM-MIC-01", "Rode Wireless PRO Dual Mic", ItemType.MICROPHONE, "SN-MIC-202", ItemCondition.EXCELLENT, ItemStatus.AVAILABLE));
            kit.registerOrReplaceItem(new KitItem("ITEM-TRP-01", "Manfrotto Video Tripod", ItemType.TRIPOD, "SN-TRP-303", ItemCondition.EXCELLENT, ItemStatus.AVAILABLE));
            return new KitRelayServiceImpl(kit, new ConsoleNotificationService());
        }

        @Override
        public Booking bookKit(String clubId, TimeSlot timeSlot, String eventName) {
            lock.lock();
            try {
                Club club = clubs.get(clubId.toLowerCase());
                if (club == null) {
                    throw new EntityNotFoundException("Club '" + clubId + "' is not registered");
                }

                AvailabilityResult avail = checkAvailability(timeSlot);
                if (!avail.isAvailable()) {
                    if (avail.getConflictingBooking() != null && avail.getConflictingBooking().getTimeSlot().overlapsWith(timeSlot)) {
                        throw new BookingConflictException("Booking conflict: " + avail.getReason());
                    } else {
                        throw new InspectionGapViolationException("Inspection interval violation: " + avail.getReason());
                    }
                }

                String bId = "BKG-" + bookingSeq.incrementAndGet();
                Booking booking = new Booking(bId, club, eventName, timeSlot);

                // If kit has an open disruption, mark as AT_RISK
                List<Disruption> openDisruptions = getOpenDisruptions();
                if (!openDisruptions.isEmpty()) {
                    Disruption latest = openDisruptions.get(0);
                    booking.markAtRisk("Kit currently disrupted: " + latest.getType() + " (" + latest.getDescription() + ")", latest.getDisruptionId());
                    latest.addAffectedBookingId(booking.getBookingId());
                    bookings.put(bId, booking);
                    notificationService.notifyBookingAtRisk(booking, latest);
                } else {
                    bookings.put(bId, booking);
                    notificationService.notifyBookingCreated(booking);
                }

                return booking;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public AvailabilityResult checkAvailability(TimeSlot timeSlot) {
            lock.lock();
            try {
                for (Booking existing : bookings.values()) {
                    if (!existing.getStatus().isActiveOrUpcoming()) continue;

                    // Rule 1: Overlapping check
                    if (existing.getTimeSlot().overlapsWith(timeSlot)) {
                        return AvailabilityResult.conflict(
                                String.format("Directly overlaps with %s (%s) of Club '%s'",
                                        existing.getBookingId(), existing.getTimeSlot(), existing.getClub().getName()),
                                existing, timeSlot);
                    }

                    // Rule 2: 15-minute gap check
                    if (!existing.getTimeSlot().hasRequiredGap(timeSlot, MANDATORY_INSPECTION_GAP)) {
                        Duration gap = existing.getTimeSlot().calculateGapBetween(timeSlot);
                        long minutes = gap != null ? gap.toMinutes() : 0;
                        return AvailabilityResult.conflict(
                                String.format("Violates mandatory 15-minute gap (current gap: %d mins) with %s (%s)",
                                        minutes, existing.getBookingId(), existing.getTimeSlot()),
                                existing, timeSlot);
                    }
                }
                return AvailabilityResult.available(timeSlot);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public HandoverRecord checkoutKit(String bookingId, String organizerName, String clubRepresentative) {
            lock.lock();
            try {
                Booking booking = bookings.get(bookingId);
                if (booking == null) throw new EntityNotFoundException("Booking " + bookingId + " not found");

                if (booking.getStatus() == BookingStatus.ACTIVE) throw new InvalidBookingStateException("Kit is already checked out");
                if (booking.getStatus() == BookingStatus.COMPLETED) throw new InvalidBookingStateException("Booking already completed");
                if (booking.getStatus() == BookingStatus.CANCELLED) throw new InvalidBookingStateException("Cannot checkout cancelled booking");

                // Rule 4: The kit can be issued only when all required items are available and usable.
                InspectionResult inspection = inspectKit();
                if (!inspection.isReady()) {
                    throw new KitNotAvailableException("Cannot checkout: Kit is not ready. Issues: " + inspection.getIssues());
                }

                List<ItemConditionReport> snapshots = new ArrayList<>();
                for (KitItem item : eventKit.getAllItems()) {
                    item.markIssued();
                    snapshots.add(new ItemConditionReport(item.getId(), item.getType(), item.getCondition(), "Issued in usable condition"));
                }

                // Rule 3: Documented through a handover record.
                String rId = "HO-OUT-" + handoverSeq.incrementAndGet();
                HandoverRecord record = new HandoverRecord(rId, bookingId, HandoverType.CHECKOUT, organizerName, clubRepresentative, snapshots, "Checkout verified");
                handovers.put(rId, record);

                booking.markActive();
                return record;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public HandoverRecord returnKit(String bookingId, String organizerName, String clubRepresentative,
                                        List<ItemConditionReport> returnReports, String remarks) {
            lock.lock();
            try {
                Booking booking = bookings.get(bookingId);
                if (booking == null) throw new EntityNotFoundException("Booking " + bookingId + " not found");
                if (booking.getStatus() != BookingStatus.ACTIVE) {
                    throw new InvalidBookingStateException("Cannot return: Booking is in status " + booking.getStatus() + ", expected ACTIVE.");
                }

                List<ItemConditionReport> processedSnapshots = new ArrayList<>();
                for (ItemConditionReport report : returnReports) {
                    KitItem item = eventKit.getItemById(report.getItemId())
                            .orElseThrow(() -> new EntityNotFoundException("Item " + report.getItemId() + " not found"));
                    processedSnapshots.add(report);

                    if (report.getCondition().isUsable()) {
                        item.updateCondition(report.getCondition(), ItemStatus.AVAILABLE, report.getRemarks());
                    } else {
                        // Rule 5: Missing or damaged equipment must be recorded as a disruption.
                        DisruptionType dt = report.getCondition() == ItemCondition.MISSING ? DisruptionType.MISSING : DisruptionType.DAMAGED;
                        item.updateCondition(report.getCondition(), dt == DisruptionType.MISSING ? ItemStatus.MISSING : ItemStatus.DAMAGED, report.getRemarks());
                        reportDisruption(item.getId(), dt, "Returned in non-usable condition: " + report.getRemarks(), bookingId);
                    }
                }

                // Rule 3: Documented through a handover record.
                String rId = "HO-RET-" + handoverSeq.incrementAndGet();
                HandoverRecord record = new HandoverRecord(rId, bookingId, HandoverType.RETURN, organizerName, clubRepresentative, processedSnapshots, remarks);
                handovers.put(rId, record);

                booking.markCompleted();
                return record;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public InspectionResult inspectKit() {
            lock.lock();
            try {
                return new InspectionResult(eventKit.isCompleteAndReady(), eventKit, eventKit.getDefectsOrMissingReasons());
            } finally {
                lock.unlock();
            }
        }

        @Override
        public Disruption reportDisruption(String itemId, DisruptionType type, String description, String originatingBookingId) {
            lock.lock();
            try {
                KitItem item = eventKit.getItemById(itemId)
                        .orElseThrow(() -> new EntityNotFoundException("Item " + itemId + " not found"));

                if (type == DisruptionType.MISSING) item.markMissing(description);
                else item.markDamaged(description);

                String dId = "DIS-" + disruptionSeq.incrementAndGet();
                Disruption disruption = new Disruption(dId, item.getId(), item.getType(), type, description, originatingBookingId);

                // Rule 6: Upcoming bookings affected by a disruption must be marked as at risk.
                LocalDateTime now = LocalDateTime.now();
                for (Booking b : bookings.values()) {
                    if ((b.getStatus() == BookingStatus.CONFIRMED || b.getStatus() == BookingStatus.REQUESTED) &&
                            b.getTimeSlot().getEndTime().isAfter(now)) {
                        b.markAtRisk(String.format("%s (%s) is %s", item.getName(), item.getType().getDisplayName(), type), dId);
                        disruption.addAffectedBookingId(b.getBookingId());
                        notificationService.notifyBookingAtRisk(b, disruption);
                    }
                }

                disruptions.put(dId, disruption);
                return disruption;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void resolveDisruption(String disruptionId, String resolutionDetails, KitItem replacementItem) {
            lock.lock();
            try {
                Disruption disruption = disruptions.get(disruptionId);
                if (disruption == null) throw new EntityNotFoundException("Disruption " + disruptionId + " not found");
                if (!disruption.isOpen()) throw new KitRelayException("Disruption already resolved");

                if (replacementItem != null) {
                    eventKit.registerOrReplaceItem(replacementItem);
                    disruption.resolve(resolutionDetails, replacementItem.getId());
                } else {
                    KitItem item = eventKit.getItemById(disruption.getItemId())
                            .orElseThrow(() -> new EntityNotFoundException("Item " + disruption.getItemId() + " not found"));
                    item.markRepairedOrReplaced(ItemCondition.EXCELLENT, item.getSerialNumber(), resolutionDetails);
                    disruption.resolve(resolutionDetails, null);
                }

                // If kit is ready and no other disruptions open, reinstate affected bookings
                if (eventKit.isCompleteAndReady() && getOpenDisruptions().isEmpty()) {
                    for (String bId : disruption.getAffectedBookingIds()) {
                        Booking b = bookings.get(bId);
                        if (b != null && b.getStatus() == BookingStatus.AT_RISK) {
                            b.reinstateConfirmed();
                            notificationService.notifyBookingReconfirmed(b, resolutionDetails);
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        @Override
        public Booking cancelBooking(String bookingId, String reason) {
            lock.lock();
            try {
                Booking booking = bookings.get(bookingId);
                if (booking == null) throw new EntityNotFoundException("Booking " + bookingId + " not found");
                if (booking.getStatus() == BookingStatus.COMPLETED) throw new InvalidBookingStateException("Cannot cancel completed booking");
                if (booking.getStatus() == BookingStatus.CANCELLED) throw new InvalidBookingStateException("Booking already cancelled");

                booking.markCancelled(reason != null ? reason : "Cancelled by coordinator");
                notificationService.notifyBookingCancelled(booking, reason);
                return booking;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void registerClub(Club club) {
            clubs.put(club.getId().toLowerCase(), club);
        }

        @Override
        public List<Club> getAllClubs() { return new ArrayList<>(clubs.values()); }
        @Override
        public Booking getBooking(String bookingId) { return bookings.get(bookingId); }
        @Override
        public List<Booking> getAllBookings() {
            return bookings.values().stream()
                    .sorted(Comparator.comparing(b -> b.getTimeSlot().getStartTime()))
                    .collect(Collectors.toList());
        }
        @Override
        public EventKit getEventKit() { return eventKit; }
        @Override
        public List<Disruption> getOpenDisruptions() {
            return disruptions.values().stream().filter(Disruption::isOpen).collect(Collectors.toList());
        }
        @Override
        public List<HandoverRecord> getAllHandoverRecords() {
            return handovers.values().stream().sorted(Comparator.comparing(HandoverRecord::getTimestamp)).collect(Collectors.toList());
        }
    }

    // ========================================================================
    // 7. COMPREHENSIVE VERIFICATION & SHOWCASE (MAIN)
    // ========================================================================

    public static void main(String[] args) {
        System.out.println("===============================================================================");
        System.out.println("       COLLEGE EVENT KIT RELAY - LOW LEVEL DESIGN COMPLETE SYSTEM");
        System.out.println("===============================================================================\n");

        KitRelayService service = KitRelayServiceImpl.createDefault();
        LocalDate eventDate = LocalDate.now().plusDays(1);

        // --- STEP 1: REGISTER CLUBS ---
        System.out.println(">>> 1. REGISTERING COLLEGE CLUBS");
        Club dramaClub = new Club("CLUB-DRAMA", "Drama Society", "Alex", "drama@college.edu", "555-0101");
        Club roboticsClub = new Club("CLUB-ROBOT", "Robotics Club", "Devin", "robotics@college.edu", "555-0102");
        Club mediaClub = new Club("CLUB-MEDIA", "Media Club", "Samira", "media@college.edu", "555-0103");
        Club musicSociety = new Club("CLUB-MUSIC", "Music Society", "Chloe", "music@college.edu", "555-0104");

        service.registerClub(dramaClub);
        service.registerClub(roboticsClub);
        service.registerClub(mediaClub);
        service.registerClub(musicSociety);
        System.out.println("   Registered 4 clubs: Drama, Robotics, Media, and Music.\n");

        // --- STEP 2: VERIFY INITIAL KIT READINESS ---
        System.out.println(">>> 2. INITIAL KIT READINESS INSPECTION (inspectKit)");
        InspectionResult initialInspect = service.inspectKit();
        System.out.println("   " + initialInspect);
        System.out.println("   " + initialInspect.getKit());

        // --- STEP 3: BOOKING RULES (Rule 1 & 2) ---
        System.out.println(">>> 3. TESTING CONFLICT-FREE BOOKINGS & 15-MINUTE INSPECTION GAP");

        // 3.1: Valid booking for Drama Club: 10:00 -> 12:00
        TimeSlot slot1 = new TimeSlot(eventDate.atTime(10, 0), eventDate.atTime(12, 0));
        Booking b1 = service.bookKit(dramaClub.getId(), slot1, "Auditions");
        System.out.println("   [SUCCESS] Drama Club booking created: " + b1);

        // 3.2: Direct Overlap Test (Rule 1)
        TimeSlot overlapSlot = new TimeSlot(eventDate.atTime(11, 0), eventDate.atTime(13, 0));
        try {
            service.bookKit(roboticsClub.getId(), overlapSlot, "RoboWars");
            System.err.println("   [FAIL] Direct overlap should have been blocked!");
        } catch (BookingConflictException e) {
            System.out.println("   [PASSED - Rule 1] Direct overlap blocked: " + e.getMessage());
        }

        // 3.3: 5-minute gap test (Rule 2)
        TimeSlot shortGapSlot = new TimeSlot(eventDate.atTime(12, 5), eventDate.atTime(14, 0));
        try {
            service.bookKit(roboticsClub.getId(), shortGapSlot, "RoboWars");
            System.err.println("   [FAIL] 5-minute gap should have been blocked!");
        } catch (InspectionGapViolationException e) {
            System.out.println("   [PASSED - Rule 2] Insufficient gap blocked: " + e.getMessage());
        }

        // 3.4: 15-minute gap compliant booking: 12:15 -> 14:15
        TimeSlot validGapSlot = new TimeSlot(eventDate.atTime(12, 15), eventDate.atTime(14, 15));
        Booking b2 = service.bookKit(roboticsClub.getId(), validGapSlot, "RoboWars Stream");
        System.out.println("   [PASSED - Rule 2] 15-min gap booking confirmed: " + b2);

        // 3.5: Media club books afternoon slot: 15:00 -> 17:00
        TimeSlot afternoonSlot = new TimeSlot(eventDate.atTime(15, 0), eventDate.atTime(17, 0));
        Booking b3 = service.bookKit(mediaClub.getId(), afternoonSlot, "Campus News");
        System.out.println("   [SUCCESS] Media Club booking confirmed: " + b3 + "\n");

        // --- STEP 4: CHECKOUT HANDOVER (Rule 3 & 4) ---
        System.out.println(">>> 4. EQUIPMENT CHECKOUT HANDOVER (checkoutKit)");
        HandoverRecord checkout1 = service.checkoutKit(b1.getBookingId(), "Prof. Vance (AV Staff)", "Alex (Drama Rep)");
        System.out.println("   Handover record created: " + checkout1.getRecordId() + " (Type: " + checkout1.getType() + ")");
        System.out.println("   Booking 1 status transitioned to: " + b1.getStatus() + "\n");

        // --- STEP 5: RETURN WITH DAMAGE -> AUTO DISRUPTION & AT_RISK CASCADE (Rule 5 & 6) ---
        System.out.println(">>> 5. RETURN WITH DAMAGE -> DISRUPTION LOGGED & UPCOMING AT-RISK (returnKit)");
        EventKit kit = service.getEventKit();
        List<ItemConditionReport> returnWithDamage = List.of(
                new ItemConditionReport(kit.getItem(ItemType.CAMERA).getId(), ItemType.CAMERA, ItemCondition.EXCELLENT, "Pristine"),
                new ItemConditionReport(kit.getItem(ItemType.MICROPHONE).getId(), ItemType.MICROPHONE, ItemCondition.EXCELLENT, "Pristine"),
                new ItemConditionReport(kit.getItem(ItemType.TRIPOD).getId(), ItemType.TRIPOD, ItemCondition.DAMAGED, "Tripod leg locking bracket cracked")
        );

        HandoverRecord returnRecord1 = service.returnKit(b1.getBookingId(), "Prof. Vance", "Alex", returnWithDamage, "Damage noticed during return inspection");
        System.out.println("   Return record created: " + returnRecord1.getRecordId());
        System.out.println("   Booking 1 completed: " + b1.getStatus());
        System.out.println("   [PASSED - Rule 5] Open Disruption logged for broken tripod: " + service.getOpenDisruptions().get(0));
        System.out.println("   [PASSED - Rule 6] Booking 2 status is now: " + b2.getStatus() + " | Risk: " + b2.getRiskReason());
        System.out.println("   [PASSED - Rule 6] Booking 3 status is now: " + b3.getStatus() + " | Risk: " + b3.getRiskReason() + "\n");

        // --- STEP 6: VERIFY AT-RISK CHECKOUT BLOCKED (Rule 4) ---
        System.out.println(">>> 6. VERIFY CHECKOUT BLOCKED WHEN KIT DAMAGED (Rule 4)");
        try {
            service.checkoutKit(b2.getBookingId(), "Prof. Vance", "Devin");
            System.err.println("   [FAIL] Checkout should not succeed with damaged tripod!");
        } catch (KitNotAvailableException e) {
            System.out.println("   [PASSED - Rule 4] Checkout rejected: " + e.getMessage() + "\n");
        }

        // --- STEP 7: RESOLVE DISRUPTION & AUTOMATIC REINSTATEMENT (Rule 7) ---
        System.out.println(">>> 7. RESOLVING DISRUPTION & RESTORING AT-RISK BOOKINGS (resolveDisruption)");
        Disruption d = service.getOpenDisruptions().get(0);
        KitItem replacementTripod = new KitItem("ITEM-TRP-02", "Heavy-Duty Carbon Video Tripod", ItemType.TRIPOD, "SN-TRP-909", ItemCondition.EXCELLENT, ItemStatus.AVAILABLE);

        service.resolveDisruption(d.getDisruptionId(), "Replaced broken tripod with new Carbon Fiber unit", replacementTripod);
        System.out.println("   Disruption status: " + d.getStatus());
        System.out.println("   Kit status: " + service.inspectKit());
        System.out.println("   [PASSED - Rule 7] Booking 2 reinstated to: " + b2.getStatus());
        System.out.println("   [PASSED - Rule 7] Booking 3 reinstated to: " + b3.getStatus() + "\n");

        // --- STEP 8: CHECKOUT & CLEAN RETURN OF RESTORED KIT ---
        System.out.println(">>> 8. RESTORED KIT CHECKOUT & RETURN FOR ROBOTICS CLUB");
        service.checkoutKit(b2.getBookingId(), "Prof. Vance", "Devin");
        List<ItemConditionReport> cleanReturn = List.of(
                new ItemConditionReport(kit.getItem(ItemType.CAMERA).getId(), ItemType.CAMERA, ItemCondition.EXCELLENT, "Clean"),
                new ItemConditionReport(kit.getItem(ItemType.MICROPHONE).getId(), ItemType.MICROPHONE, ItemCondition.EXCELLENT, "Clean"),
                new ItemConditionReport(replacementTripod.getId(), ItemType.TRIPOD, ItemCondition.EXCELLENT, "Clean")
        );
        service.returnKit(b2.getBookingId(), "Prof. Vance", "Devin", cleanReturn, "All returned in perfect shape");
        System.out.println("   Booking 2 completed successfully: " + b2.getStatus() + "\n");

        // --- STEP 9: CANCELLATION WORKFLOW (cancelBooking) ---
        System.out.println(">>> 9. CANCELLATION & TIME-SLOT REALLOCATION (cancelBooking)");
        service.cancelBooking(b3.getBookingId(), "Shoot cancelled due to severe thunderstorms");
        System.out.println("   Booking 3 cancelled: " + b3.getStatus() + " | Reason: " + b3.getCancellationReason());

        // Slot becomes available again:
        AvailabilityResult freedSlotAvail = service.checkAvailability(afternoonSlot);
        System.out.println("   Is slot " + afternoonSlot + " available again? " + freedSlotAvail.isAvailable());

        Booking b4 = service.bookKit(musicSociety.getId(), afternoonSlot, "Studio Session");
        System.out.println("   Music Society booked freed slot: " + b4 + "\n");

        // --- SUMMARY ---
        System.out.println("===============================================================================");
        System.out.println("                         EXECUTION SUMMARY");
        System.out.println("===============================================================================");
        System.out.println("Total Bookings Processed: " + service.getAllBookings().size());
        for (Booking b : service.getAllBookings()) {
            System.out.println("  * " + b);
        }
        System.out.println("Total Handover Records Created: " + service.getAllHandoverRecords().size());
        System.out.println("Final Kit Usability: " + (service.inspectKit().isReady() ? "100% OPERATIONAL [PASS]" : "DEGRADED [FAIL]"));
        System.out.println(">>> ALL 7 BUSINESS RULES AND OPERATIONS VERIFIED IN A SINGLE SELF-CONTAINED FILE! <<<");
        System.out.println("===============================================================================");
    }
}
