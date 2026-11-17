package com.example.demo.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.demo.common.BusinessException;
import com.example.demo.policy.PolicyService;
import com.example.demo.policy.ReservationPolicy;
import com.example.demo.seat.Seat;
import com.example.demo.seat.SeatRepository;
import com.example.demo.seat.SeatStatusChangedEvent;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

/**
 * US-05 AC5: DB(Repository)를 Mock 으로 대체해 중복 예약 방지 규칙을 독립적으로 검증한다.
 * 기준 시각: 2026-11-17 10:00 (Asia/Seoul)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReservationServiceTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);
    private static final long USER = 1L;
    private static final long SEAT = 10L;

    @Mock ReservationRepository reservations;
    @Mock SeatRepository seats;
    @Mock PolicyService policies;
    @Mock ApplicationEventPublisher events;

    ReservationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(LocalDateTime.of(TODAY, LocalTime.of(10, 0)).atZone(ZONE).toInstant(), ZONE);
        service = new ReservationService(reservations, seats, policies, events, clock);
        when(policies.getPolicy(any())).thenReturn(
                new ReservationPolicy(7, 4, LocalTime.of(9, 0), LocalTime.of(22, 0), 30));
        when(reservations.lockMember(USER)).thenReturn(true);
        when(seats.findByIdForUpdate(SEAT)).thenReturn(Optional.of(new Seat(SEAT, 5, 2, "제2열람실", true)));
        when(reservations.insertReserved(anyLong(), anyLong(), any())).thenReturn(100L);
    }

    private static TimeSlot slot(LocalDate date, String start, String end) {
        return TimeSlot.parse(date.toString(), start, end);
    }

    @Test
    @DisplayName("AC1: 가용 좌석이면 RESERVED 상태로 생성되고 좌석 변경 이벤트를 발행한다")
    void createsReservedReservation() {
        TimeSlot slot = slot(TODAY.plusDays(1), "10:00", "12:00");

        ReservationResult result = service.createReservation(USER, SEAT, slot);

        assertThat(result.status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(result.reservationId()).isEqualTo(100L);
        assertThat(result.seatNumber()).isEqualTo(5);
        verify(reservations).insertReserved(USER, SEAT, slot);
        verify(events).publishEvent(new SeatStatusChangedEvent(SEAT, 2, slot.date()));
    }

    @Test
    @DisplayName("AC2: 같은 학생이 겹치는 시간대에 예약을 가지고 있으면 거부한다 (좌석은 잠그지도 않음)")
    void rejectsWhenUserAlreadyHasOverlappingReservation() {
        TimeSlot slot = slot(TODAY.plusDays(1), "10:00", "12:00");
        when(reservations.existsUserOverlap(USER, slot)).thenReturn(true);

        assertThatThrownBy(() -> service.createReservation(USER, SEAT, slot))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 예약한 좌석")
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);
        verify(seats, never()).findByIdForUpdate(anyLong());
        verify(reservations, never()).insertReserved(anyLong(), anyLong(), any());
        verifyNoInteractions(events);
    }

    @Test
    @DisplayName("AC2/AC3: 좌석이 겹치는 시간대에 이미 예약되어 있으면 거부한다")
    void rejectsWhenSeatAlreadyReserved() {
        TimeSlot slot = slot(TODAY.plusDays(1), "10:00", "12:00");
        when(reservations.existsSeatOverlap(SEAT, slot)).thenReturn(true);

        assertThatThrownBy(() -> service.createReservation(USER, SEAT, slot))
                .isInstanceOf(BusinessException.class)
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);
        verify(reservations, never()).insertReserved(anyLong(), anyLong(), any());
        verifyNoInteractions(events);
    }

    @Test
    @DisplayName("AC3: 좌석 행을 잠근 뒤에 좌석 중복을 검사한다 (회원 잠금 → 좌석 잠금 순서)")
    void locksMemberThenSeatBeforeOverlapCheck() {
        TimeSlot slot = slot(TODAY.plusDays(1), "10:00", "12:00");

        service.createReservation(USER, SEAT, slot);

        var order = org.mockito.Mockito.inOrder(reservations, seats);
        order.verify(reservations).lockMember(USER);
        order.verify(reservations).existsUserOverlap(USER, slot);
        order.verify(seats).findByIdForUpdate(SEAT);
        order.verify(reservations).existsSeatOverlap(SEAT, slot);
        order.verify(reservations).insertReserved(USER, SEAT, slot);
    }

    @Test
    @DisplayName("AC4: 오늘+7일을 넘는 날짜는 허용 범위를 안내하며 거부한다")
    void rejectsBeyondAdvanceWindow() {
        TimeSlot slot = slot(TODAY.plusDays(8), "10:00", "12:00");

        assertThatThrownBy(() -> service.createReservation(USER, SEAT, slot))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2026-11-17부터 2026-11-24까지");
        verifyNoInteractions(seats);
        verify(reservations, never()).lockMember(anyLong());
    }

    @Test
    @DisplayName("AC4: 오늘+7일 당일은 예약할 수 있다 (경계값)")
    void acceptsLastDayOfAdvanceWindow() {
        TimeSlot slot = slot(TODAY.plusDays(7), "20:00", "22:00");

        assertThat(service.createReservation(USER, SEAT, slot).status()).isEqualTo(ReservationStatus.RESERVED);
    }

    @Test
    @DisplayName("AC4: 4시간을 넘으면 최대 이용 시간을 안내하며 거부한다")
    void rejectsLongerThanMaxHours() {
        TimeSlot slot = slot(TODAY.plusDays(1), "10:00", "14:30");

        assertThatThrownBy(() -> service.createReservation(USER, SEAT, slot))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("최대 4시간");
        verifyNoInteractions(seats);
    }

    @Test
    @DisplayName("지난 시각은 예약할 수 없다")
    void rejectsPastTime() {
        TimeSlot slot = slot(TODAY, "09:00", "11:00");

        assertThatThrownBy(() -> service.createReservation(USER, SEAT, slot))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("지난 시간");
    }

    @Test
    @DisplayName("없는 좌석은 404, 사용 중지 좌석은 409")
    void rejectsMissingOrInactiveSeat() {
        TimeSlot slot = slot(TODAY.plusDays(1), "10:00", "12:00");
        when(seats.findByIdForUpdate(SEAT)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createReservation(USER, SEAT, slot))
                .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);

        when(seats.findByIdForUpdate(SEAT)).thenReturn(Optional.of(new Seat(SEAT, 5, 2, "제2열람실", false)));
        assertThatThrownBy(() -> service.createReservation(USER, SEAT, slot))
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("회원 행이 없으면 500 대신 401 로 응답한다")
    void rejectsUnknownMember() {
        when(reservations.lockMember(USER)).thenReturn(false);

        assertThatThrownBy(() -> service.createReservation(USER, SEAT, slot(TODAY.plusDays(1), "10:00", "12:00")))
                .extracting("status").isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
