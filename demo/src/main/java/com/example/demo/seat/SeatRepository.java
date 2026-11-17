package com.example.demo.seat;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** seats 테이블 접근. 모든 SQL 은 파라미터 바인딩(?)만 사용한다. */
@Repository
public class SeatRepository {
    private static final String COLUMNS = "seat_id, seat_number, floor, room_name, is_active";

    private final JdbcTemplate jdbc;

    public SeatRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 해당 층의 사용 가능한 좌석을 번호순으로. */
    public List<Seat> findActiveByFloor(int floor) {
        return jdbc.query("SELECT " + COLUMNS + " FROM seats WHERE floor = ? AND is_active = 'Y' ORDER BY seat_number",
                SeatRepository::map, floor);
    }

    /** 좌석 행을 비관적 잠금(SELECT ... FOR UPDATE)하고 읽는다. 같은 좌석에 대한 동시 예약을 직렬화한다. */
    public Optional<Seat> findByIdForUpdate(long seatId) {
        List<Seat> rows = jdbc.query("SELECT " + COLUMNS + " FROM seats WHERE seat_id = ? FOR UPDATE",
                SeatRepository::map, seatId);
        return rows.stream().findFirst();
    }

    private static Seat map(ResultSet rs, int row) throws SQLException {
        return new Seat(rs.getLong("seat_id"), rs.getInt("seat_number"), rs.getInt("floor"),
                rs.getString("room_name"), "Y".equals(rs.getString("is_active")));
    }
}
