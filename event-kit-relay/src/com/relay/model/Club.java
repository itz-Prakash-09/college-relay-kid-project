package com.relay.model;

import java.util.Objects;

/**
 * Represents a college club that requests and uses the event kit.
 */
public class Club {
    private final String id;
    private final String name;
    private final String contactPerson;
    private final String email;
    private final String phone;

    public Club(String id, String name, String contactPerson, String email, String phone) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Club ID cannot be null or empty");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Club name cannot be null or empty");
        }
        this.id = id.trim();
        this.name = name.trim();
        this.contactPerson = contactPerson != null ? contactPerson.trim() : "";
        this.email = email != null ? email.trim() : "";
        this.phone = phone != null ? phone.trim() : "";
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getContactPerson() {
        return contactPerson;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Club club)) return false;
        return Objects.equals(id, club.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("Club[id='%s', name='%s', contact='%s', email='%s']", id, name, contactPerson, email);
    }
}
