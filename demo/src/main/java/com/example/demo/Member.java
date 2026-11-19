package com.example.demo;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** 회원 테이블 (설계서 p.8 User 엔티티). */
@Entity
@Table(name = "APP_MEMBERS")
class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true, nullable = false)
    private String studentid;

    @Column(unique = true, nullable = false)
    private String email;

    private String passwordHash;

    private String status;

    private String role;

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getStudentid() { return studentid; }
    public String getPasswordHash() { return passwordHash; }
    public String getStatus() { return status; }
    public String getRole() { return role; }

    protected Member() {}

    public Member(String name, String studentid, String email, String passwordHash) {
        this.name = name;
        this.studentid = studentid;
        this.email = email;
        this.passwordHash = passwordHash;
        this.status = "PENDING";
        this.role = "ROLE_STUDENT";
        issueVerificationToken();
    }

    @Column(unique = true)
    private String verificationToken;

    private LocalDateTime verificationTokenExpiresAt;

    public String getEmail() {
        return email;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    public void issueVerificationToken() {
        this.verificationToken = UUID.randomUUID().toString();
        this.verificationTokenExpiresAt =
                LocalDateTime.now().plusMinutes(30);
    }

    public boolean isVerificationExpired() {
        return verificationTokenExpiresAt == null ||
                LocalDateTime.now().isAfter(verificationTokenExpiresAt);
    }

    public void activate() {
        this.status = "ACTIVE";
        this.verificationToken = null;
        this.verificationTokenExpiresAt = null;
    }
}
