package com.example.demo.reservation;

/** 예약 생성 결과. */
public record ReservationResult(long reservationId, long seatId, int floor, int seatNumber,
                                TimeSlot slot, ReservationStatus status) {
}
