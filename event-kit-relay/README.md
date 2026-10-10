# College Event Kit Relay – Low-Level Design (LLD)

## 📌 Project Overview
A centralized, object-oriented system enabling multiple college clubs to share and reserve a common event production kit consisting of a **camera**, **microphone**, and **tripod**. The system guarantees conflict-free reservations, enforces mandatory inspection intervals, captures equipment handovers with item-level snapshots, and provides resilient disruption handling and resolution.

---

## 🏗️ Architecture & Class Structure

### 1. Core Classes & Responsibilities

| Class | Responsibility | Key Attributes / Methods |
|---|---|---|
| **`Club`** | Represents a student club requesting the kit. | `id`, `name`, `contactPerson`, `email`, `phone` |
| **`KitItem`** | Models an individual equipment piece; tracks physical condition & operational availability. | `id`, `name`, `type` (`CAMERA`, `MICROPHONE`, `TRIPOD`), `serialNumber`, `condition`, `status`, `isUsable()`, `markIssued()`, `markDamaged()` |
| **`EventKit`** | Aggregates required kit items; verifies complete equipment suite readiness. | `items`, `requiredTypes`, `isCompleteAndReady()`, `getDefectsOrMissingReasons()` |
| **`TimeSlot`** | Immutable temporal window with overlap and buffer gap checking. | `startTime`, `endTime`, `overlapsWith()`, `hasRequiredGap(minGap)` |
| **`Booking`** | Maintains reservation lifecycle state for a club event. | `bookingId`, `club`, `timeSlot`, `status` (`CONFIRMED`, `AT_RISK`, `ACTIVE`, `COMPLETED`, `CANCELLED`) |
| **`HandoverRecord`** | Immutable audit record documenting checkout or return with item snapshots. | `recordId`, `bookingId`, `type` (`CHECKOUT`, `RETURN`), `handledBy`, `clubRepresentative`, `itemSnapshots` |
| **`Disruption`** | Tracks equipment-related defects/losses and linked affected bookings. | `disruptionId`, `itemId`, `type` (`MISSING`, `DAMAGED`), `status` (`OPEN`, `RESOLVED`), `affectedBookingIds`, `resolve()` |
| **`KitRelayService`** | Central service facade coordinating bookings, handovers, inspections, disruptions, and cancellations. | `bookKit()`, `checkAvailability()`, `checkoutKit()`, `returnKit()`, `inspectKit()`, `reportDisruption()`, `resolveDisruption()`, `cancelBooking()` |

---

## 🔒 Key Business Rules Implementation

1. **Conflict-Free Bookings**:
   - `checkAvailability()` verifies that no existing active booking (`CONFIRMED`, `AT_RISK`, `ACTIVE`) overlaps with the requested time window.
2. **Mandatory 15-Minute Inspection Gap**:
   - For consecutive reservations, a strict $\ge 15\text{ minute}$ buffer is enforced. Requests within $< 15$ minutes trigger `InspectionGapViolationException`.
3. **Traceable Handovers**:
   - Both checkout and return generate immutable `HandoverRecord` entries documenting organizer, club representative, timestamp, and per-item status.
4. **All-or-Nothing Kit Issuance**:
   - The kit cannot be checked out unless all 3 components (camera, mic, tripod) are present, usable, and not damaged/missing (`KitNotAvailableException`).
5. **Automated Disruption Reporting**:
   - If an item is returned damaged or missing, a `Disruption` record is immediately created.
6. **Proactive At-Risk Alerts**:
   - All upcoming bookings dependent on the broken equipment are transitioned to `AT_RISK`, and urgent notifications are dispatched.
7. **Resolution or Cancellation**:
   - Disruptions are resolved via equipment repair or replacement, automatically restoring at-risk bookings to `CONFIRMED`. If unfulfillable, bookings are cleanly cancelled, freeing up slots.

---

## 🚀 Execution Instructions

### Prerequisites
- JDK 17, 21, or 24+ (`javac` and `java` available on PATH).

### Running Tests
Execute the automated test suite directly:
```cmd
run_tests.bat
```
Or manually:
```cmd
dir /s /b src\*.java > sources.txt
javac -d bin @sources.txt
java -cp bin com.relay.test.KitRelayTestSuite
```

### Running the End-to-End Showcase Demo
Execute the full console walkthrough:
```cmd
run_demo.bat
```
Or manually:
```cmd
java -cp bin com.relay.Main
```
