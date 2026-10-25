package com.abes.hcl.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Locale;

@Entity
@Table(name = "candidates")
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(length = 30)
    private String phone;

    protected Candidate() {
    }

    public Candidate(String name, String email, String phone) {
        this.name = name.trim();
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.phone = phone == null || phone.isBlank() ? null : phone.trim();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }

    public void update(String name, String email, String phone) {
        this.name = name.trim();
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.phone = phone == null || phone.isBlank() ? null : phone.trim();
    }
}
