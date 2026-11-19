package com.example.demo.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.example.demo.common.BusinessException;
import com.example.demo.policy.PolicyService;
import com.example.demo.seat.SeatRepository;
import com.example.demo.seat.SeatStatusChangedEvent;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ReservationHistoryServiceTest {
    private static final long USER = 7L;
    private static final long ID = 31L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 10, 0);
    @Mock ReservationRepository reservations;
    @Mock SeatRepository seats;
    @Mock PolicyService policies;
    @Mock ApplicationEventPublisher events;
    ReservationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
        service = new ReservationService(reservations, seats, policies, events, clock);
        lenient().when(reservations.isActiveStudent(USER)).thenReturn(true);
    }

    @Test
    void historyUsesOnlyAuthenticatedUserAndIncludesAllStates() {
        var past = new ReservationHistoryItem(1, 2, 1, 3, "A", "2026-11-17", "10:00", "11:00",
                "2026-11-17T10:00+09:00", "2026-11-17T11:00+09:00", "RESERVED", false, null);
        var cancelled = new ReservationHistoryItem(2, 2, 1, 3, "A", "2026-11-18", "11:00", "12:00",
                "2026-11-18T11:00+09:00", "2026-11-18T12:00+09:00", "CANCELLED", false, "2026-11-18T09:00+09:00");
        when(reservations.findHistory(USER, NOW)).thenReturn(List.of(cancelled, past));
        assertThat(service.getMyReservations(USER)).containsExactly(cancelled, past);
        verify(reservations).findHistory(USER, NOW);
        when(reservations.isActiveStudent(8)).thenReturn(true);
        when(reservations.findHistory(8, NOW)).thenReturn(List.of());
        assertThat(service.getMyReservations(8)).isEmpty();
    }

    @Test
    void cancelBeforeStartPublishesSeatChangeOnce() {
        var target = new ReservationRepository.CancellationTarget(5, 2, NOW.plusMinutes(1));
        when(reservations.findCancellationTarget(ID, USER)).thenReturn(Optional.of(target));
        when(reservations.cancelIfAllowed(ID, USER, NOW)).thenReturn(1);
        service.cancel(USER, ID);
        verify(events).publishEvent(new SeatStatusChangedEvent(5, 2, NOW.toLocalDate()));
        verifyNoInteractions(policies, seats);
    }

    @Test
    void anotherUsersReservationIsHidden() {
        when(reservations.findCancellationTarget(ID, USER)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.cancel(USER, ID)).isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(reservations, never()).cancelIfAllowed(anyLong(), anyLong(), any());
        verifyNoInteractions(events);
    }

    @Test
    void startBoundaryAndRepeatedCancelAreRejectedByConditionalUpdate() {
        when(reservations.findCancellationTarget(ID, USER))
                .thenReturn(Optional.of(new ReservationRepository.CancellationTarget(5, 2, NOW)));
        when(reservations.cancelIfAllowed(ID, USER, NOW)).thenReturn(0);
        assertThatThrownBy(() -> service.cancel(USER, ID)).isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.cancel(USER, ID)).isInstanceOf(BusinessException.class);
        verify(reservations, times(2)).cancelIfAllowed(ID, USER, NOW);
        verifyNoInteractions(events);
    }

    @Test
    void inactiveOrNonStudentCannotReadOrCancel() {
        when(reservations.isActiveStudent(USER)).thenReturn(false);
        assertThatThrownBy(() -> service.getMyReservations(USER)).isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.cancel(USER, ID)).isInstanceOf(BusinessException.class);
        verify(reservations, never()).findHistory(anyLong(), any());
        verify(reservations, never()).cancelIfAllowed(anyLong(), anyLong(), any());
    }
}
