package com.relay.repository;

import com.relay.model.EventKit;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe repository for maintaining the shared EventKit.
 */
public class KitRepository {
    private final AtomicReference<EventKit> currentKit = new AtomicReference<>();

    public KitRepository(EventKit initialKit) {
        this.currentKit.set(initialKit);
    }

    public EventKit getKit() {
        return currentKit.get();
    }

    public void setKit(EventKit kit) {
        if (kit == null) throw new IllegalArgumentException("Kit cannot be null");
        currentKit.set(kit);
    }

    public Optional<EventKit> findKit() {
        return Optional.ofNullable(currentKit.get());
    }
}
