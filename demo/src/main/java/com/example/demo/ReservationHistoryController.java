package com.example.demo;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.context.ApplicationEventPublisher;
import com.example.demo.seat.SeatStatusChangedEvent;

/** 기존 예약 컨트롤러를 수정하지 않고 추가하는 전체 이력 API. */
@RestController
@RequestMapping("/api/reservations/history")
public class ReservationHistoryController {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private final JdbcTemplate jdbc;

    private final ApplicationEventPublisher events;

    public ReservationHistoryController(JdbcTemplate jdbc, ApplicationEventPublisher events) { this.jdbc = jdbc; this.events = events;}

    public record HistoryItem(long id, long seat_id, Integer floor, Integer seat_number,
                              String room_name, String date, String start_time, String end_time,
                              String starts_at, String ends_at, String status, boolean can_cancel) {}

    @GetMapping
    public List<HistoryItem> history(@AuthenticationPrincipal Jwt user) {
        LocalDateTime now = LocalDateTime.now(KOREA);
        // 취소/지난 예약을 제외하지 않습니다. 좌석 정보가 없어져도 예약 이력은 남깁니다.
        return jdbc.query("""
                SELECT r.reservation_id, r.seat_id, r.start_time, r.end_time, r.status,
                       s.floor, s.seat_number, s.room_name
                FROM reservations r
                LEFT JOIN seats s ON s.seat_id = r.seat_id
                WHERE r.user_id = ?
                ORDER BY r.start_time DESC, r.reservation_id DESC
                """, (rs, row) -> {
            LocalDateTime start = rs.getTimestamp("start_time").toLocalDateTime();
            LocalDateTime end = rs.getTimestamp("end_time").toLocalDateTime();
            String storedStatus = rs.getString("status");
            String status = storedStatus;
            if ("RESERVED".equals(storedStatus)) {
                status = !end.isAfter(now) ? "COMPLETED" : !start.isAfter(now) ? "IN_USE" : "RESERVED";
            }
            return new HistoryItem(rs.getLong("reservation_id"), rs.getLong("seat_id"),
                    rs.getObject("floor", Integer.class), rs.getObject("seat_number", Integer.class),
                    rs.getString("room_name"), start.toLocalDate().toString(),
                    start.format(HH_MM), end.format(HH_MM),
                    start.atZone(KOREA).toOffsetDateTime().toString(),
                    end.atZone(KOREA).toOffsetDateTime().toString(), status,
                    "RESERVED".equals(storedStatus) && start.isAfter(now));
        }, Long.parseLong(user.getSubject()));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, String> cancel(
            @AuthenticationPrincipal Jwt user,
            @PathVariable long id) {
        long userId = Long.parseLong(user.getSubject());
        // 기존 예약 취소 로직
        int affected = jdbc.update("""
                UPDATE reservations SET status = 'CANCELLED'
                WHERE reservation_id = ? AND user_id = ? AND status = 'RESERVED'
                AND start_time > ?
                """, id, userId, Timestamp.valueOf(LocalDateTime.now(KOREA)));
        if (affected == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "취소할 수 있는 본인 예약이 없습니다. 이미 취소되었거나 이용이 시작되었을 수 있습니다.");
        }
        // 취소한 예약의 좌석 정보 조회
        SeatStatusChangedEvent event = jdbc.queryForObject("""
                SELECT r.seat_id, s.floor, r.start_time
                FROM reservations r
                JOIN seats s ON r.seat_id = s.seat_id
                WHERE r.reservation_id = ? AND r.user_id = ?
                """,
                (rs, rowNum) -> new SeatStatusChangedEvent(
                        rs.getLong("seat_id"),
                        rs.getInt("floor"),
                        rs.getTimestamp("start_time")
                        .toLocalDateTime().toLocalDate()
                ),
                id, userId
        );
        // SSE 이벤트 발행
        events.publishEvent(event);
        return Map.of("message", "예약이 취소되었습니다.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handle(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode())
                .body(Map.of("message", error.getReason() == null ? "예약 요청에 실패했습니다." : error.getReason()));
    }
}
