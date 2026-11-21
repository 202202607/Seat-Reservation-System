package com.example.demo.seat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.demo.policy.PolicyService;
import com.example.demo.policy.ReservationPolicy;
import com.example.demo.reservation.ReservationRepository;
import com.example.demo.reservation.ReservationStatus;
import com.example.demo.reservation.SeatOccupancy;
import com.example.demo.reservation.TimeSlot;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** US-04 AC2: 예약 가능 / 사용 중 / 내 예약 / 예약됨 4가지 상태를 구분한다. */
@ExtendWith(MockitoExtension.class)
class SeatServiceTest {
    private static final long ME = 1L;
    private static final long OTHER = 2L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Mock SeatRepository seats;
    @Mock ReservationRepository reservations;
    @Mock PolicyService policies;

    @Test
    void classifiesFourSeatStates() {
        Clock clock = Clock.fixed(ZonedDateTime.of(2026, 11, 17, 10, 0, 0, 0, ZONE).toInstant(), ZONE);
        SeatService service = new SeatService(seats, reservations, policies, clock);
        TimeSlot slot = TimeSlot.parse("2026-11-17", "11:00", "13:00");
        when(seats.findActiveByFloor(1)).thenReturn(List.of(
                new Seat(1, 1, 1, "제1열람실", true),
                new Seat(2, 2, 1, "제1열람실", true),
                new Seat(3, 3, 1, "제1열람실", true),
                new Seat(4, 4, 1, "제1열람실", true),
                new Seat(5, 5, 1, "제1열람실", true)));
        when(reservations.findOccupyingOnFloor(1, slot)).thenReturn(List.of(
                new SeatOccupancy(2, OTHER, ReservationStatus.CHECKED_IN),
                new SeatOccupancy(3, ME, ReservationStatus.RESERVED),
                new SeatOccupancy(4, OTHER, ReservationStatus.RESERVED),
                new SeatOccupancy(5, ME, ReservationStatus.CHECKED_IN)));
        when(policies.getPolicy(any())).thenReturn(
                new ReservationPolicy(7, 4, LocalTime.of(9, 0), LocalTime.of(22, 0), 30));

        SeatService.SeatStatusResult result = service.getSeatStatus(ME, 1, slot);

        assertThat(result.seats()).extracting(SeatService.SeatWithStatus::status).containsExactly(
                SeatStatus.AVAILABLE, SeatStatus.IN_USE, SeatStatus.MINE, SeatStatus.RESERVED, SeatStatus.MINE);
        assertThat(result.today()).isEqualTo(LocalDate.of(2026, 11, 17));
    }

    @Test
    void mineTakesPriorityOverOthers() {
        assertThat(SeatStatus.of(List.of(
                new SeatOccupancy(1, OTHER, ReservationStatus.CHECKED_IN),
                new SeatOccupancy(1, ME, ReservationStatus.RESERVED)), ME)).isEqualTo(SeatStatus.MINE);
        assertThat(SeatStatus.of(List.of(
                new SeatOccupancy(1, OTHER, ReservationStatus.RESERVED),
                new SeatOccupancy(1, 3L, ReservationStatus.CHECKED_IN)), ME)).isEqualTo(SeatStatus.IN_USE);
        assertThat(SeatStatus.of(List.of(), ME)).isEqualTo(SeatStatus.AVAILABLE);
    }
}
