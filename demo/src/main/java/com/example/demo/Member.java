package com.example.demo;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** 회원 테이블 (설계서 p.8 User 엔티티). */
@Entity
@Table(name = "APP_MEMBERS")
class Member {
    static final String ROLE_STUDENT = "ROLE_STUDENT";
    static final String ROLE_ADMIN = "ROLE_ADMIN";
    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_ACTIVE = "ACTIVE";

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

    /** 학생 가입: PENDING + ROLE_STUDENT, 이메일 인증 후 ACTIVE (US-01 AC1, AC4). */
    public Member(String name, String studentid, String email, String passwordHash) {
        this.name = name;
        this.studentid = studentid;
        this.email = email;
        this.passwordHash = passwordHash;
        this.status = STATUS_PENDING;
        this.role = ROLE_STUDENT;
        issueVerificationToken();
    }

    /**
     * 관리자 가입: 바로 ACTIVE + ROLE_ADMIN (US-01 AC5, 설계서 p.4 Core Flow 1 save(ACTIVE, ROLE_ADMIN)).
     * 관리자 ID는 학번과 같은 로그인 식별자 열(studentid)에 저장한다.
     */
    static Member admin(String name, String adminId, String email, String passwordHash) {
        Member member = new Member();
        member.name = name;
        member.studentid = adminId;
        member.email = email;
        member.passwordHash = passwordHash;
        member.status = STATUS_ACTIVE;
        member.role = ROLE_ADMIN;
        return member;
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
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
        this.status = STATUS_ACTIVE;
        this.verificationToken = null;
        this.verificationTokenExpiresAt = null;
    }
}
