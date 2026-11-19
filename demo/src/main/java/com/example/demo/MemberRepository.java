package com.example.demo;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 회원 저장·조회 (설계서 p.4 UserRepository). */
interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByStudentid(String studentid);
    boolean existsByEmail(String email);
    boolean existsByStudentid(String studentid);
    Optional<Member> findByVerificationToken(String token);

    Optional<Member> findByEmail(String email);
}
