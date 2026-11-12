package com.example.demo;

import java.sql.Timestamp;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class SeatReservationController {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private final JdbcTemplate jdbc;

    public SeatReservationController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/seats")
    public List<Map<String, Object>> seats(
            @AuthenticationPrincipal Jwt user,
            @RequestParam int floor,
            @RequestParam String date,
            @RequestParam String start,
            @RequestParam String end) {
        LocalDateTime startAt = parseTime(date, start);
        LocalDateTime endAt = parseTime(date, end);
        requireChronologicalRange(startAt, endAt);

        // [start, end) intervals overlap iff old.start < new.end AND old.end > new.start.
        String sql = """
                SELECT s.seat_id, s.seat_number, s.floor, s.room_name, s.is_active,
                       CASE
                         WHEN EXISTS (
                           SELECT 1 FROM reservations r
                           WHERE r.seat_id = s.seat_id AND r.user_id = ?
                             AND r.status = 'RESERVED'
                             AND r.start_time < ? AND r.end_time > ?
                         ) THEN 'MINE'
                         WHEN EXISTS (
                           SELECT 1 FROM reservations r
                           WHERE r.seat_id = s.seat_id
                             AND r.status = 'RESERVED'
                             AND r.start_time < ? AND r.end_time > ?
                         ) THEN 'RESERVED'
                         ELSE 'AVAILABLE'
                       END AS display_status
                FROM seats s
                WHERE s.floor = ? AND s.is_active = 'Y'
                ORDER BY s.seat_number
                """;
        
        long userId = user == null
        ? -1L
        : Long.parseLong(user.getSubject());

        return jdbc.query(sql, (rs, row) -> Map.of(
                "seat_id", rs.getLong("seat_id"),
                "seat_number", rs.getInt("seat_number"),
                "floor", rs.getInt("floor"),
                "room_name", rs.getString("room_name"),
                "is_active", rs.getString("is_active"),
                "display_status", rs.getString("display_status")
        ), userId,
           Timestamp.valueOf(endAt), Timestamp.valueOf(startAt),
           Timestamp.valueOf(endAt), Timestamp.valueOf(startAt), floor);
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

    public record ReservationRequest(Long seat_id, String date, String start_time, String end_time) {}

    @PostMapping("/reservations")
    @Transactional
    public Map<String, String> reserve(@AuthenticationPrincipal Jwt user, @RequestBody ReservationRequest request) {
        if (request == null || request.seat_id() == null || request.date() == null
                || request.start_time() == null || request.end_time() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "예약 정보가 부족합니다.");
        }
        LocalDateTime startAt = parseTime(request.date(), request.start_time());
        LocalDateTime endAt = parseTime(request.date(), request.end_time());
        requireChronologicalRange(startAt, endAt);
        if (Duration.between(startAt, endAt).toMinutes() > 240) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "최대 4시간까지만 예약할 수 있습니다.");
        }
        if (startAt.toLocalTime().isBefore(LocalTime.of(9, 0))
                || endAt.toLocalTime().isAfter(LocalTime.of(22, 0))
                || startAt.getMinute() % 30 != 0 || endAt.getMinute() % 30 != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "운영시간(09:00~22:00)과 30분 단위를 지켜 주세요.");
        }
        if (!startAt.isAfter(LocalDateTime.now(KOREA))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지난 시간은 예약할 수 없습니다.");
        }

        // Lock a single seat to serialize competing bookings across requests/instances.
        // Transaction is held until overlap check and INSERT finish.
        Long userId = Long.parseLong(user.getSubject());
        // 사용자 잠금
        jdbc.queryForObject(
            "SELECT id FROM APP_MEMBERS WHERE id = ? FOR UPDATE",
            Long.class,
            userId
        );
        String active;
        try {
            active = jdbc.queryForObject(
                    "SELECT is_active FROM seats WHERE seat_id = ? FOR UPDATE",
                    String.class, request.seat_id());
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "좌석이 존재하지 않습니다.");
        }
        if (!"Y".equals(active)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "현재 사용할 수 없는 좌석입니다.");
        }

        Integer existing = jdbc.queryForObject("""
                SELECT COUNT(*) FROM reservations
                WHERE seat_id = ? AND status = 'RESERVED'
                  AND start_time < ? AND end_time > ?
                """, Integer.class, request.seat_id(),
                Timestamp.valueOf(endAt), Timestamp.valueOf(startAt));
        if (existing != null && existing > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 예약된 시간입니다.");
        }
        Integer userExisting = jdbc.queryForObject("""
            SELECT COUNT(*) FROM reservations
            WHERE user_id = ? AND status = 'RESERVED'
            AND start_time < ? AND end_time > ?
            """, Integer.class, Long.parseLong(user.getSubject()),
            Timestamp.valueOf(endAt), Timestamp.valueOf(startAt));

        if (userExisting != null && userExisting > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "해당 시간에 이미 예약한 좌석이 있습니다.");
        }
        jdbc.update("""
                INSERT INTO reservations (seat_id, user_id, start_time, end_time, status)
                VALUES (?, ?, ?, ?, 'RESERVED')
                """, request.seat_id(), Long.parseLong(user.getSubject()),
                Timestamp.valueOf(startAt), Timestamp.valueOf(endAt));
        return Map.of("message", "예약이 완료되었습니다.");
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

    private static LocalDateTime parseTime(String date, String time) {
        try {
            return LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time));
        } catch (DateTimeParseException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "날짜 또는 시간 형식이 잘못되었습니다.");
        }
    }

    private static void requireChronologicalRange(LocalDateTime startAt, LocalDateTime endAt) {
        if (!endAt.isAfter(startAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "종료 시간은 시작 시간보다 늦어야 합니다.");
        }
    }
}
