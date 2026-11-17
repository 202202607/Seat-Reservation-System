package com.example.demo.seat;

/** 좌석 (seats 테이블). */
public record Seat(long seatId, int seatNumber, int floor, String roomName, boolean active) {
}
