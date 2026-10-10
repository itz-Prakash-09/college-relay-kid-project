package com.relay.repository;

import com.relay.model.HandoverRecord;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe repository for storing immutable handover checkout/return records.
 */
public class HandoverRepository {
    private final Map<String, HandoverRecord> records = new ConcurrentHashMap<>();

    public void save(HandoverRecord record) {
        if (record == null) throw new IllegalArgumentException("Record cannot be null");
        records.put(record.getRecordId(), record);
    }

    public Optional<HandoverRecord> findById(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(records.get(id));
    }

    public List<HandoverRecord> findByBookingId(String bookingId) {
        if (bookingId == null) return Collections.emptyList();
        return records.values().stream()
                .filter(r -> r.getBookingId().equalsIgnoreCase(bookingId.trim()))
                .sorted(Comparator.comparing(HandoverRecord::getTimestamp))
                .collect(Collectors.toList());
    }

    public List<HandoverRecord> findAll() {
        return records.values().stream()
                .sorted(Comparator.comparing(HandoverRecord::getTimestamp))
                .collect(Collectors.toList());
    }

    public void clear() {
        records.clear();
    }
}
