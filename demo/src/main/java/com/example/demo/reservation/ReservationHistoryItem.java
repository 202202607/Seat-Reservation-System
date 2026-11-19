package com.example.demo.reservation;

/** status is the stored state; the client may derive a time-based label separately. */
public record ReservationHistoryItem(long id, long seat_id, Integer floor, Integer seat_number,
        String room_name, String date, String start_time, String end_time,
        String starts_at, String ends_at, String status, boolean can_cancel,
        String cancelled_at,
        /** RESERVED 예약을 취소할 수 있는 마지막 시각 (시작 + 체크인 제한 시간). 그 외 상태는 null. */
        String cancel_until) {
}
