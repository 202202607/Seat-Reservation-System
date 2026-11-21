package com.example.demo;

import com.example.demo.common.CurrentUser;
import com.example.demo.reservation.ReservationHistoryItem;
import com.example.demo.reservation.ReservationService;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Existing history URLs delegate to the same service as the designed URLs. */
@RestController
@RequestMapping("/api/reservations/history")
public class ReservationHistoryController {
    private final ReservationService service;

    public ReservationHistoryController(ReservationService service) {
        this.service = service;
    }

    @GetMapping
    public List<ReservationHistoryItem> history(@AuthenticationPrincipal Jwt user) {
        return service.getMyReservations(CurrentUser.id(user));
    }

    @DeleteMapping("/{id}")
    public Map<String, String> cancel(@AuthenticationPrincipal Jwt user, @PathVariable long id) {
        service.cancel(CurrentUser.id(user), id);
        return Map.of("message", "예약이 취소되었습니다.");
    }
}
