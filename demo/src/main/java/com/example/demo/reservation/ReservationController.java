package com.example.demo.reservation;

import com.example.demo.common.BusinessException;
import com.example.demo.common.CurrentUser;
import java.time.format.DateTimeFormatter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
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

    /** 요청 본문. 기존 프론트와 같은 snake_case 필드명을 유지한다. */
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
}
