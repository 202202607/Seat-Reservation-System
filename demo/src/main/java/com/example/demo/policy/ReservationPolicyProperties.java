package com.example.demo.policy;

import java.time.LocalTime;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 평상시 예약 정책 값 (application.properties의 app.reservation.policy.*).
 * 백로그 p.2 "정책 값 하드코딩 0건" 을 위해 코드 대신 설정에서 읽는다.
 */
@ConfigurationProperties(prefix = "app.reservation.policy")
public record ReservationPolicyProperties(
        @DefaultValue("7") int maxAdvanceDays,
        @DefaultValue("4") int maxReservationHours,
        @DefaultValue("09:00") LocalTime openTime,
        @DefaultValue("22:00") LocalTime closeTime,
        @DefaultValue("30") int slotMinutes) {

    public ReservationPolicy toPolicy() {
        return new ReservationPolicy(maxAdvanceDays, maxReservationHours, openTime, closeTime, slotMinutes);
    }
}
