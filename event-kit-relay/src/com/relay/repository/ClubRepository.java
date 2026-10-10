package com.relay.repository;

import com.relay.model.Club;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe repository for managing Club entities.
 */
public class ClubRepository {
    private final Map<String, Club> clubs = new ConcurrentHashMap<>();

    public void save(Club club) {
        if (club == null) throw new IllegalArgumentException("Club cannot be null");
        clubs.put(club.getId().toLowerCase(), club);
    }

    public Optional<Club> findById(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(clubs.get(id.toLowerCase()));
    }

    public List<Club> findAll() {
        return new ArrayList<>(clubs.values());
    }

    public boolean existsById(String id) {
        if (id == null) return false;
        return clubs.containsKey(id.toLowerCase());
    }

    public void clear() {
        clubs.clear();
    }
}
