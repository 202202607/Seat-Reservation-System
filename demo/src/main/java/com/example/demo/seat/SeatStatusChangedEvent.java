package com.example.demo.seat;

import java.time.LocalDate;

/**
 * 좌석 점유 상태가 바뀌었음을 알리는 이벤트. 트랜잭션 커밋 후 SSE 구독자에게 전달된다.
 * 예약 생성 외에 취소(US-09)·체크인(US-08)·자동 취소(US-11)에서도 같은 이벤트를 발행하면
 * 좌석 화면이 바로 갱신된다.
 */
public record SeatStatusChangedEvent(long seatId, int floor, LocalDate date) {
}
