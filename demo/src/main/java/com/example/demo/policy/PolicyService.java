package com.example.demo.policy;

import java.time.Duration;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * 날짜별로 적용할 예약 정책을 돌려준다 (설계서 Core Flow 2 의 getPolicy(date)).
 * Sprint 1 은 평상시 정책만 설정 파일에서 읽는다.
 * US-18(시험 기간 정책)에서 Policy 테이블 조회를 이 클래스 안에 추가하면 호출부는 바뀌지 않는다.
 */
@Service
public class PolicyService {
    private final ReservationPolicyProperties defaults;

    public PolicyService(ReservationPolicyProperties defaults) {
        this.defaults = defaults;
    }

    public ReservationPolicy getPolicy(LocalDate date) {
        return defaults.toPolicy();
    }

    /** 체크인 제한 시간. 예약 시작부터 이 시간 동안은 '체크인 대기' 상태로 본다. */
    public Duration checkInWindow() {
        return Duration.ofMinutes(defaults.checkInMinutes());
    }
}
