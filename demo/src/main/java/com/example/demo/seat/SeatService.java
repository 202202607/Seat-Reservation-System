package com.example.demo.seat;

import com.example.demo.policy.PolicyService;
import com.example.demo.policy.ReservationPolicy;
import com.example.demo.reservation.ReservationRepository;
import com.example.demo.reservation.SeatOccupancy;
import com.example.demo.reservation.TimeSlot;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 날짜·시간대별 좌석 현황 조회 (US-04, 설계서 Core Flow 2 의 getSeatStatus). */
@Service
public class SeatService {
    private final SeatRepository seats;
    private final ReservationRepository reservations;
    private final PolicyService policies;
    private final Clock clock;

    public SeatService(SeatRepository seats, ReservationRepository reservations,
                       PolicyService policies, Clock clock) {
        this.seats = seats;
        this.reservations = reservations;
        this.policies = policies;
        this.clock = clock;
    }

    public record SeatWithStatus(Seat seat, SeatStatus status) {}

    /** 좌석 현황과 함께, 화면이 예약 가능 범위를 안내할 수 있도록 적용 정책을 돌려준다. */
    public record SeatStatusResult(TimeSlot slot, LocalDate today, ReservationPolicy policy,
                                   List<SeatWithStatus> seats) {}

    /**
     * 층의 좌석 목록과 시간대가 겹치는 점유 예약을 각각 한 번씩 조회한 뒤 메모리에서 상태를 계산한다.
     * (좌석마다 서브쿼리를 돌리지 않으므로 좌석 수가 늘어도 쿼리는 2개로 고정)
     */
    @Transactional(readOnly = true)
    public SeatStatusResult getSeatStatus(long viewerId, int floor, TimeSlot slot) {
        List<Seat> floorSeats = seats.findActiveByFloor(floor);
        Map<Long, List<SeatOccupancy>> bySeat = reservations.findOccupyingOnFloor(floor, slot).stream()
                .collect(Collectors.groupingBy(SeatOccupancy::seatId));

        List<SeatWithStatus> result = floorSeats.stream()
                .map(seat -> new SeatWithStatus(seat,
                        SeatStatus.of(bySeat.getOrDefault(seat.seatId(), List.of()), viewerId)))
                .toList();
        return new SeatStatusResult(slot, LocalDate.now(clock), policies.getPolicy(slot.date()), result);
    }
}
