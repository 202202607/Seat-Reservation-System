package com.example.demo.reservation;

import com.example.demo.common.BusinessException;
import com.example.demo.policy.PolicyService;
import com.example.demo.policy.ReservationPolicy;
import com.example.demo.seat.Seat;
import com.example.demo.seat.SeatRepository;
import com.example.demo.seat.SeatStatusChangedEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 좌석 예약 생성 (US-05, 설계서 p.6 "좌석 예약 생성" 시퀀스). */
@Service
public class ReservationService {
    private final ReservationRepository reservations;
    private final SeatRepository seats;
    private final PolicyService policies;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ReservationService(ReservationRepository reservations, SeatRepository seats,
                              PolicyService policies, ApplicationEventPublisher events, Clock clock) {
        this.reservations = reservations;
        this.seats = seats;
        this.policies = policies;
        this.events = events;
        this.clock = clock;
    }

    /**
     * 정책 검사 → 회원 잠금 → 본인 중복 검사 → 좌석 잠금 → 좌석 중복 검사 → 저장 순서로 처리한다.
     * 잠금 순서(회원 → 좌석)를 항상 같게 유지해 교착 상태를 피한다.
     */
    @Transactional
    public ReservationResult createReservation(long userId, long seatId, TimeSlot slot) {
        // TODO(US-11): 이용 제한(Restriction) 중인 학생은 여기서 거부한다.
        ReservationPolicy policy = policies.getPolicy(slot.date());
        policy.validate(slot, LocalDateTime.now(clock));

        if (!reservations.lockMember(userId)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "회원 정보를 찾을 수 없습니다. 다시 로그인해 주세요.");
        }
        if (reservations.existsUserOverlap(userId, slot)) {
            throw BusinessException.conflict("해당 시간대에 이미 예약한 좌석이 있습니다.");
        }

        Seat seat = seats.findByIdForUpdate(seatId)
                .orElseThrow(() -> BusinessException.notFound("좌석이 존재하지 않습니다."));
        if (!seat.active()) {
            throw BusinessException.conflict("현재 사용할 수 없는 좌석입니다.");
        }
        if (reservations.existsSeatOverlap(seatId, slot)) {
            throw BusinessException.conflict("이미 다른 학생이 예약한 시간대입니다.");
        }

        long id = reservations.insertReserved(userId, seatId, slot);
        events.publishEvent(new SeatStatusChangedEvent(seatId, seat.floor(), slot.date()));
        return new ReservationResult(id, seatId, seat.floor(), seat.seatNumber(), slot, ReservationStatus.RESERVED);
    }

    public List<ReservationHistoryItem> getMyReservations(long userId) {
        requireActiveStudent(userId);
        return reservations.findHistory(userId, LocalDateTime.now(clock), policies.checkInWindow());
    }

    /**
     * US-09 AC1: '예약중(RESERVED)' 또는 '체크인 대기'(시작 후 체크인 제한 시간 이내, 미체크인)이면 취소할 수 있다.
     * AC4: 체크인 완료·취소됨·노쇼(AUTO_CANCELLED)는 거부한다. 학생 취소는 노쇼 카운터를 바꾸지 않는다(AC2).
     */
    @Transactional
    public void cancel(long userId, long reservationId) {
        requireActiveStudent(userId);
        var target = reservations.findCancellationTarget(reservationId, userId)
                .orElseThrow(() -> BusinessException.notFound("본인 예약을 찾을 수 없습니다."));
        LocalDateTime now = LocalDateTime.now(clock);
        Duration window = policies.checkInWindow();
        if (reservations.cancelIfAllowed(reservationId, userId, now, now.minus(window)) != 1) {
            throw BusinessException.conflict("이미 취소·체크인되었거나 체크인 제한 시간(시작 후 "
                    + window.toMinutes() + "분)이 지나 취소할 수 없습니다.");
        }
        if (target.floor() != null) {
            events.publishEvent(new SeatStatusChangedEvent(target.seatId(), target.floor(), target.start().toLocalDate()));
        }
    }

    private void requireActiveStudent(long userId) {
        if (!reservations.isActiveStudent(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "활성 학생 계정만 이용할 수 있습니다.");
        }
    }
}
