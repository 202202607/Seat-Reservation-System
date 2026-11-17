package com.example.demo.policy;

import com.example.demo.common.BusinessException;
import com.example.demo.reservation.TimeSlot;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 예약 생성 시 지켜야 하는 정책 값과 검증 규칙 (US-05 AC4, 설계서 p.10 Business Rules).
 * DB 나 시각에 의존하지 않는 순수 객체라 단위 테스트로 바로 검증할 수 있다.
 */
public record ReservationPolicy(
        int maxAdvanceDays,
        int maxReservationHours,
        LocalTime openTime,
        LocalTime closeTime,
        int slotMinutes) {

    public ReservationPolicy {
        if (maxAdvanceDays < 0 || maxReservationHours <= 0 || slotMinutes <= 0
                || openTime == null || closeTime == null || !closeTime.isAfter(openTime)) {
            throw new IllegalArgumentException("예약 정책 설정값이 올바르지 않습니다.");
        }
    }

    /** 예약 가능한 마지막 날짜 (오늘 + maxAdvanceDays, 포함). */
    public LocalDate lastBookableDate(LocalDate today) {
        return today.plusDays(maxAdvanceDays);
    }

    public Duration maxDuration() {
        return Duration.ofHours(maxReservationHours);
    }

    /**
     * 요청한 시간대가 정책을 지키는지 검사한다. 위반하면 허용 범위를 안내하는 메시지로 예외를 던진다.
     */
    public void validate(TimeSlot slot, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalDate lastDate = lastBookableDate(today);
        if (slot.date().isBefore(today) || slot.date().isAfter(lastDate)) {
            throw BusinessException.badRequest(
                    "예약은 %s부터 %s까지(오늘~+%d일) 가능합니다.".formatted(today, lastDate, maxAdvanceDays));
        }
        if (!slot.start().isAfter(now)) {
            throw BusinessException.badRequest("이미 지난 시간은 예약할 수 없습니다.");
        }
        if (slot.duration().compareTo(maxDuration()) > 0) {
            throw BusinessException.badRequest(
                    "1회 최대 %d시간까지 예약할 수 있습니다.".formatted(maxReservationHours));
        }
        if (slot.start().toLocalTime().isBefore(openTime) || slot.end().toLocalTime().isAfter(closeTime)
                || !onSlotBoundary(slot.start().toLocalTime()) || !onSlotBoundary(slot.end().toLocalTime())) {
            throw BusinessException.badRequest(
                    "운영 시간(%s~%s) 안에서 %d분 단위로 예약해 주세요.".formatted(openTime, closeTime, slotMinutes));
        }
    }

    private boolean onSlotBoundary(LocalTime time) {
        return time.getSecond() == 0 && time.getNano() == 0
                && (time.getHour() * 60 + time.getMinute()) % slotMinutes == 0;
    }
}
