package com.example.demo;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * US-06(내 예약)·US-09(예약 취소) 담당 영역의 기존 엔드포인트.
 * 좌석 조회(US-04)는 seat.SeatController, 예약 생성(US-05)은 reservation.ReservationController 로 옮겼다.
 */
@RestController
@RequestMapping("/api")
public class SeatReservationController {
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private final JdbcTemplate jdbc;

    public SeatReservationController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/reservations/my")
    public List<Map<String, Object>> myReservations(@AuthenticationPrincipal Jwt user) {
        String sql = """
                SELECT r.reservation_id, r.seat_id, r.start_time, r.end_time,
                       s.floor, s.seat_number
                FROM reservations r
                JOIN seats s ON s.seat_id = r.seat_id
                WHERE r.user_id = ? AND r.status = 'RESERVED'
                ORDER BY r.start_time
                """;
        return jdbc.query(sql, (rs, row) -> {
            LocalDateTime startAt = rs.getTimestamp("start_time").toLocalDateTime();
            LocalDateTime endAt = rs.getTimestamp("end_time").toLocalDateTime();
            return Map.<String, Object>of(
                    "id", rs.getLong("reservation_id"),
                    "seat_id", rs.getLong("seat_id"),
                    "floor", rs.getInt("floor"),
                    "seat_number", rs.getInt("seat_number"),
                    "date", startAt.toLocalDate().toString(),
                    "start_time", startAt.toLocalTime().format(HH_MM),
                    "end_time", endAt.toLocalTime().format(HH_MM),
                    "status", "RESERVED"
            );
        }, Long.parseLong(user.getSubject()));
    }

    @DeleteMapping("/reservations/{id}")
    @Transactional
    public Map<String, String> cancel(@AuthenticationPrincipal Jwt user, @PathVariable long id) {
        int affected = jdbc.update("""
                UPDATE reservations SET status = 'CANCELLED'
                WHERE reservation_id = ? AND user_id = ? AND status = 'RESERVED'
                """, id, Long.parseLong(user.getSubject()));
        if (affected == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "취소할 본인 예약이 없습니다.");
        }
        return Map.of("message", "예약이 취소되었습니다.");
    }
}
