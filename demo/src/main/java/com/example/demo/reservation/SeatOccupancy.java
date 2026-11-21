package com.example.demo.reservation;

/** 조회 시간대와 겹치는, 좌석을 점유 중인(RESERVED/CHECKED_IN) 예약 한 건. */
public record SeatOccupancy(long seatId, long userId, ReservationStatus status) {
}
