package com.example.demo.reservation;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * reservations 테이블 접근.
 * 두 구간 [s1, e1), [s2, e2) 는 s1 < e2 AND e1 > s2 일 때 겹친다.
 */
@Repository
public class ReservationRepository {
    private final JdbcTemplate jdbc;

    public ReservationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 해당 층에서 시간대가 겹치는 RESERVED/CHECKED_IN 예약 목록 (US-04). */
    public List<SeatOccupancy> findOccupyingOnFloor(int floor, TimeSlot slot) {
        return jdbc.query("""
                SELECT r.seat_id, r.user_id, r.status
                FROM reservations r
                JOIN seats s ON s.seat_id = r.seat_id
                WHERE s.floor = ?
                  AND r.status IN (?, ?)
                  AND r.start_time < ? AND r.end_time > ?
                """,
                (rs, row) -> new SeatOccupancy(rs.getLong("seat_id"), rs.getLong("user_id"),
                        ReservationStatus.valueOf(rs.getString("status"))),
                floor, occupying(0), occupying(1), Timestamp.valueOf(slot.end()), Timestamp.valueOf(slot.start()));
    }

    /** 같은 학생이 겹치는 시간대에 이미 예약을 가지고 있는지 (US-05 AC2). */
    public boolean existsUserOverlap(long userId, TimeSlot slot) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM reservations
                WHERE user_id = ? AND status IN (?, ?)
                  AND start_time < ? AND end_time > ?
                """, Integer.class,
                userId, occupying(0), occupying(1), Timestamp.valueOf(slot.end()), Timestamp.valueOf(slot.start()));
        return count != null && count > 0;
    }

    /** 같은 좌석이 겹치는 시간대에 이미 예약되어 있는지 (US-05 AC3). */
    public boolean existsSeatOverlap(long seatId, TimeSlot slot) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM reservations
                WHERE seat_id = ? AND status IN (?, ?)
                  AND start_time < ? AND end_time > ?
                """, Integer.class,
                seatId, occupying(0), occupying(1), Timestamp.valueOf(slot.end()), Timestamp.valueOf(slot.start()));
        return count != null && count > 0;
    }

    /**
     * 회원 행을 비관적 잠금한다. 같은 학생이 서로 다른 좌석을 동시에 요청해도
     * 본인 중복 예약 검사가 경쟁 상태에 빠지지 않도록 직렬화한다.
     *
     * @return 회원이 존재하면 true
     */
    public boolean lockMember(long userId) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM APP_MEMBERS WHERE id = ? FOR UPDATE", Long.class, userId);
        return !ids.isEmpty();
    }

    /** RESERVED 상태로 예약을 저장하고 생성된 reservation_id 를 돌려준다. */
    public long insertReserved(long userId, long seatId, TimeSlot slot) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO reservations (seat_id, user_id, start_time, end_time, status)
                    VALUES (?, ?, ?, ?, ?)
                    """, new String[] {"reservation_id"});
            ps.setLong(1, seatId);
            ps.setLong(2, userId);
            ps.setTimestamp(3, Timestamp.valueOf(slot.start()));
            ps.setTimestamp(4, Timestamp.valueOf(slot.end()));
            ps.setString(5, ReservationStatus.RESERVED.name());
            return ps;
        }, keys);
        Number key = keys.getKey();
        return key == null ? -1L : key.longValue();
    }

    private static String occupying(int index) {
        return ReservationStatus.OCCUPYING.get(index).name();
    }
}
