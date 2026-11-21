package com.example.demo.reservation;

import java.util.List;

/** 예약 상태 (설계서 p.8 ERD). */
public enum ReservationStatus {
    RESERVED,
    CHECKED_IN,
    CANCELLED,
    AUTO_CANCELLED;

    /** 좌석을 점유하는 상태. 이 상태끼리는 같은 좌석·같은 학생에서 시간이 겹칠 수 없다 (설계서 p.9). */
    public static final List<ReservationStatus> OCCUPYING = List.of(RESERVED, CHECKED_IN);
}
