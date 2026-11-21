package com.example.demo.reservation;

import com.example.demo.common.BusinessException;
import com.example.demo.common.CurrentUser;
import java.util.List;
import java.util.Map;
import java.time.format.DateTimeFormatter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** POST /api/reservations (US-05). */
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    public record CreateReservationRequest(Long seat_id, String date, String start_time, String end_time) {}

    public record ReservationView(long reservation_id, long seat_id, int floor, int seat_number,
                                  String date, String start_time, String end_time, String status) {
        static ReservationView of(ReservationResult r) {
            return new ReservationView(r.reservationId(), r.seatId(), r.floor(), r.seatNumber(),
                    r.slot().date().toString(), r.slot().start().format(HH_MM), r.slot().end().format(HH_MM),
                    r.status().name());
        }
    }

    public record CreateReservationResponse(String message, ReservationView reservation) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateReservationResponse create(@AuthenticationPrincipal Jwt user,
                                            @RequestBody(required = false) CreateReservationRequest request) {
        if (request == null || request.seat_id() == null) {
            throw BusinessException.badRequest("예약할 좌석을 선택해 주세요.");
        }
        TimeSlot slot = TimeSlot.parse(request.date(), request.start_time(), request.end_time());
        ReservationResult result = reservationService.createReservation(CurrentUser.id(user), request.seat_id(), slot);
        return new CreateReservationResponse("예약이 완료되었습니다.", ReservationView.of(result));
    }

    @GetMapping("/my")
    public List<ReservationHistoryItem> my(@AuthenticationPrincipal Jwt user) {
        return reservationService.getMyReservations(CurrentUser.id(user));
    }

    @PatchMapping("/{id}/cancel")
    public Map<String, String> cancel(@AuthenticationPrincipal Jwt user, @PathVariable long id) {
        reservationService.cancel(CurrentUser.id(user), id);
        return Map.of("message", "예약이 취소되었습니다.");
    }

    @DeleteMapping("/{id}")
    public Map<String, String> cancelLegacy(@AuthenticationPrincipal Jwt user, @PathVariable long id) {
        return cancel(user, id);
    }
}
