package com.relay.notification;

import com.relay.model.Booking;
import com.relay.model.Disruption;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Standard implementation that logs notifications cleanly and retains an audit trail.
 */
public class ConsoleNotificationService implements NotificationService {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final List<String> notificationLog = Collections.synchronizedList(new ArrayList<>());
    private boolean printToConsole = true;

    public ConsoleNotificationService() {
        this(true);
    }

    public ConsoleNotificationService(boolean printToConsole) {
        this.printToConsole = printToConsole;
    }

    public void setPrintToConsole(boolean printToConsole) {
        this.printToConsole = printToConsole;
    }

    private void log(String type, String message) {
        String entry = String.format("[%s] [NOTIFICATION - %s] %s",
                LocalDateTime.now().format(TIME_FMT), type, message);
        notificationLog.add(entry);
        if (printToConsole) {
            System.out.println(entry);
        }
    }

    @Override
    public void notifyBookingCreated(Booking booking) {
        log("BOOKING_CONFIRMED", String.format("Club '%s' (Contact: %s, %s): Booking %s for '%s' confirmed on %s.",
                booking.getClub().getName(), booking.getClub().getContactPerson(),
                booking.getClub().getEmail(), booking.getBookingId(),
                booking.getEventName(), booking.getTimeSlot()));
    }

    @Override
    public void notifyBookingAtRisk(Booking booking, Disruption disruption) {
        log("BOOKING_AT_RISK", String.format(
                "URGENT ALERT to Club '%s' (%s): Your upcoming booking %s (%s) is AT RISK! " +
                "Cause: Item %s (%s) is %s [%s]. Staff is working on recovery/replacement.",
                booking.getClub().getName(), booking.getClub().getEmail(),
                booking.getBookingId(), booking.getTimeSlot(),
                disruption.getItemId(), disruption.getItemType().getDisplayName(),
                disruption.getType(), disruption.getDescription()));
    }

    @Override
    public void notifyBookingReconfirmed(Booking booking, String resolutionSummary) {
        log("BOOKING_RESTORED", String.format(
                "GOOD NEWS for Club '%s' (%s): Disruption resolved (%s). Booking %s (%s) is RE-CONFIRMED!",
                booking.getClub().getName(), booking.getClub().getEmail(),
                resolutionSummary, booking.getBookingId(), booking.getTimeSlot()));
    }

    @Override
    public void notifyBookingCancelled(Booking booking, String reason) {
        log("BOOKING_CANCELLED", String.format(
                "CANCELLATION NOTICE to Club '%s' (%s): Booking %s (%s) has been CANCELLED. Reason: %s",
                booking.getClub().getName(), booking.getClub().getEmail(),
                booking.getBookingId(), booking.getTimeSlot(), reason));
    }

    @Override
    public void notifyDisruptionLogged(Disruption disruption) {
        log("DISRUPTION_ALERT", String.format(
                "Equipment Disruption #%s logged: %s on item %s (%s). Affected bookings count: %d",
                disruption.getDisruptionId(), disruption.getType(),
                disruption.getItemId(), disruption.getItemType().getDisplayName(),
                disruption.getAffectedBookingIds().size()));
    }

    public List<String> getNotificationLog() {
        return new ArrayList<>(notificationLog);
    }

    public void clear() {
        notificationLog.clear();
    }
}
