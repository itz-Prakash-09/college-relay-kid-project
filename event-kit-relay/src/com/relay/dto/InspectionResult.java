package com.relay.dto;

import com.relay.model.EventKit;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Result returned by kit inspection operations.
 */
public class InspectionResult {
    private final boolean ready;
    private final EventKit kit;
    private final List<String> issues;
    private final LocalDateTime inspectedAt;

    public InspectionResult(boolean ready, EventKit kit, List<String> issues) {
        this.ready = ready;
        this.kit = kit;
        this.issues = issues != null ? issues : Collections.emptyList();
        this.inspectedAt = LocalDateTime.now();
    }

    public boolean isReady() {
        return ready;
    }

    public EventKit getKit() {
        return kit;
    }

    public List<String> getIssues() {
        return Collections.unmodifiableList(issues);
    }

    public LocalDateTime getInspectedAt() {
        return inspectedAt;
    }

    @Override
    public String toString() {
        if (ready) {
            return "InspectionResult: READY (All required items camera, microphone, tripod are present and usable)";
        } else {
            return "InspectionResult: NOT READY - Issues found: " + issues;
        }
    }
}
