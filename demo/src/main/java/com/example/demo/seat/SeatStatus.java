package com.example.demo.seat;

import com.example.demo.reservation.ReservationStatus;
import com.example.demo.reservation.SeatOccupancy;
import java.util.Collection;

/**
 * 조회 시간대 기준 좌석 표시 상태 (US-04 AC2: 예약 가능 / 사용 중 / 내 예약 / 예약됨).
 */
public enum SeatStatus {
    /** 겹치는 점유 예약이 없다. */
    AVAILABLE,
    /** 다른 학생이 체크인(CHECKED_IN)해 실제로 앉아 있다. */
    IN_USE,
    /** 내가 예약했거나 체크인한 좌석이다. */
    MINE,
    /** 다른 학생이 예약(RESERVED)했지만 아직 체크인하지 않았다. */
    RESERVED;

    /**
     * 한 좌석의 겹치는 점유 예약 목록으로 표시 상태를 정한다.
     * 우선순위: 내 예약 > 사용 중 > 예약됨 > 예약 가능.
     */
    public static SeatStatus of(Collection<SeatOccupancy> occupancies, long viewerId) {
        boolean inUse = false;
        boolean reserved = false;
        for (SeatOccupancy occupancy : occupancies) {
            if (occupancy.userId() == viewerId) {
                return MINE;
            }
            if (occupancy.status() == ReservationStatus.CHECKED_IN) {
                inUse = true;
            } else {
                reserved = true;
            }
        }
        if (inUse) {
            return IN_USE;
        }
        return reserved ? RESERVED : AVAILABLE;
    }
}
