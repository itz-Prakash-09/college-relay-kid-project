package com.relay.repository;

import com.relay.model.Disruption;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe repository for storing and tracking disruptions.
 */
public class DisruptionRepository {
    private final Map<String, Disruption> disruptions = new ConcurrentHashMap<>();

    public void save(Disruption disruption) {
        if (disruption == null) throw new IllegalArgumentException("Disruption cannot be null");
        disruptions.put(disruption.getDisruptionId(), disruption);
    }

    public Optional<Disruption> findById(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(disruptions.get(id));
    }

    public List<Disruption> findAll() {
        return disruptions.values().stream()
                .sorted(Comparator.comparing(Disruption::getReportedAt).reversed())
                .collect(Collectors.toList());
    }

    public List<Disruption> findOpenDisruptions() {
        return disruptions.values().stream()
                .filter(Disruption::isOpen)
                .sorted(Comparator.comparing(Disruption::getReportedAt).reversed())
                .collect(Collectors.toList());
    }

    public List<Disruption> findOpenDisruptionsByItem(String itemId) {
        if (itemId == null) return Collections.emptyList();
        return disruptions.values().stream()
                .filter(d -> d.isOpen() && d.getItemId().equalsIgnoreCase(itemId.trim()))
                .collect(Collectors.toList());
    }

    public void clear() {
        disruptions.clear();
    }
}
