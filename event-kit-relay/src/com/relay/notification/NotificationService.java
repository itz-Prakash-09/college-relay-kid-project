package com.relay.notification;

import com.relay.model.Booking;
import com.relay.model.Disruption;

/**
 * Service contract for dispatching notifications to clubs and organizers.
 */
public interface NotificationService {
    void notifyBookingCreated(Booking booking);
    void notifyBookingAtRisk(Booking booking, Disruption disruption);
    void notifyBookingReconfirmed(Booking booking, String resolutionSummary);
    void notifyBookingCancelled(Booking booking, String reason);
    void notifyDisruptionLogged(Disruption disruption);
}
