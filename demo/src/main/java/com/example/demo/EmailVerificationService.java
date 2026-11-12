package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmailVerificationService {

    private final JavaMailSender mailSender;
    private final MemberRepository memberRepository;

    @Value("${spring.mail.username}")
    private String senderAddress;

    @Value("${app.base-url}")
    private String baseUrl;

    public EmailVerificationService(
            JavaMailSender mailSender,
            MemberRepository memberRepository) {

        this.mailSender = mailSender;
        this.memberRepository = memberRepository;
    }

    public void sendVerificationEmail(Member member) {
        String link = baseUrl
                + "/api/auth/verify-email?token="
                + member.getVerificationToken();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderAddress);
        message.setTo(member.getEmail());
        message.setSubject("도서관 좌석 예약 시스템 이메일 인증");
        message.setText(
                "아래 링크를 클릭해 이메일 인증을 완료하세요.\n\n"
                + link
                + "\n\n인증 링크는 30분 동안 유효합니다."
        );

        mailSender.send(message);
    }

    @Transactional
    public void verify(String token) {
        Member member = memberRepository
                .findByVerificationToken(token)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "유효하지 않은 인증 링크입니다."
                ));

        if (member.isVerificationExpired()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "인증 링크가 만료되었습니다."
            );
        }

        member.activate();
    }
}