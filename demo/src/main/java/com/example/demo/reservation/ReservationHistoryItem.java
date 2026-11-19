package com.example.demo.reservation;

/** status is the stored state; the client may derive a time-based label separately. */
public record ReservationHistoryItem(long id, long seat_id, Integer floor, Integer seat_number,
        String room_name, String date, String start_time, String end_time,
        String starts_at, String ends_at, String status, boolean can_cancel,
        String cancelled_at) {
}
