package com.example.demo.reservation;

import com.example.demo.common.BusinessException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/** 같은 날짜 안의 [start, end) 이용 시간대. */
public record TimeSlot(LocalDateTime start, LocalDateTime end) {

    public TimeSlot {
        if (start == null || end == null) {
            throw BusinessException.badRequest("날짜와 시간을 입력해 주세요.");
        }
        if (!end.isAfter(start)) {
            throw BusinessException.badRequest("종료 시간은 시작 시간보다 늦어야 합니다.");
        }
    }

    /** "2026-11-17", "10:00", "12:00" 형식의 요청 값을 시간대로 바꾼다. */
    public static TimeSlot parse(String date, String startTime, String endTime) {
        LocalDate day;
        LocalTime start;
        LocalTime end;
        try {
            day = LocalDate.parse(date);
            start = LocalTime.parse(startTime);
            end = LocalTime.parse(endTime);
        } catch (DateTimeParseException | NullPointerException e) {
            throw BusinessException.badRequest("날짜 또는 시간 형식이 잘못되었습니다.");
        }
        return new TimeSlot(LocalDateTime.of(day, start), LocalDateTime.of(day, end));
    }

    public LocalDate date() {
        return start.toLocalDate();
    }

    public Duration duration() {
        return Duration.between(start, end);
    }
}
