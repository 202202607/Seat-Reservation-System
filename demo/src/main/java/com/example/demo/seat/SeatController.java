package com.example.demo.seat;

import com.example.demo.common.CurrentUser;
import com.example.demo.policy.ReservationPolicy;
import com.example.demo.reservation.TimeSlot;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** GET /api/seats, GET /api/seats/stream (US-04). 로그인한 사용자만 호출할 수 있다. */
@RestController
@RequestMapping("/api/seats")
public class SeatController {
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final SeatService seatService;
    private final SeatStatusBroadcaster broadcaster;

    public SeatController(SeatService seatService, SeatStatusBroadcaster broadcaster) {
        this.seatService = seatService;
        this.broadcaster = broadcaster;
    }

    public record SeatView(long seat_id, int seat_number, int floor, String room_name, String display_status) {}

    /** 화면이 날짜 선택 범위·최대 이용 시간·운영 시간을 하드코딩하지 않도록 함께 내려주는 정책 값. */
    public record PolicyView(int max_reservation_hours, int max_advance_days,
                             String bookable_from, String bookable_until,
                             String open_time, String close_time, int slot_minutes) {}

    public record SeatStatusResponse(String date, String start, String end, PolicyView policy, List<SeatView> seats) {}

    @GetMapping
    public SeatStatusResponse seats(@AuthenticationPrincipal Jwt user,
                                    @RequestParam int floor,
                                    @RequestParam String date,
                                    @RequestParam String start,
                                    @RequestParam String end) {
        TimeSlot slot = TimeSlot.parse(date, start, end);
        SeatService.SeatStatusResult result = seatService.getSeatStatus(CurrentUser.id(user), floor, slot);

        ReservationPolicy p = result.policy();
        PolicyView policy = new PolicyView(p.maxReservationHours(), p.maxAdvanceDays(),
                result.today().toString(), p.lastBookableDate(result.today()).toString(),
                p.openTime().format(HH_MM), p.closeTime().format(HH_MM), p.slotMinutes());
        List<SeatView> seats = result.seats().stream()
                .map(s -> new SeatView(s.seat().seatId(), s.seat().seatNumber(), s.seat().floor(),
                        s.seat().roomName(), s.status().name()))
                .toList();
        return new SeatStatusResponse(slot.date().toString(), slot.start().format(HH_MM),
                slot.end().format(HH_MM), policy, seats);
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return broadcaster.subscribe();
    }
}
