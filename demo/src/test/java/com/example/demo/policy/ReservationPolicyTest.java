package com.example.demo.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.common.BusinessException;
import com.example.demo.reservation.TimeSlot;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class ReservationPolicyTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 10, 0);
    private final ReservationPolicy policy = new ReservationPolicy(7, 4, LocalTime.of(9, 0), LocalTime.of(22, 0), 30);

    private static TimeSlot slot(int plusDays, String start, String end) {
        return TimeSlot.parse(LocalDate.of(2026, 11, 17).plusDays(plusDays).toString(), start, end);
    }

    @Test
    void acceptsTodayToPlusSevenWithinFourHours() {
        assertThatCode(() -> policy.validate(slot(0, "10:30", "14:30"), NOW)).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate(slot(7, "18:00", "22:00"), NOW)).doesNotThrowAnyException();
    }

    @Test
    void rejectsOutsideAdvanceWindow() {
        assertThatThrownBy(() -> policy.validate(slot(8, "10:00", "11:00"), NOW))
                .isInstanceOf(BusinessException.class).hasMessageContaining("오늘~+7일");
        assertThatThrownBy(() -> policy.validate(slot(-1, "10:00", "11:00"), NOW))
                .isInstanceOf(BusinessException.class).hasMessageContaining("오늘~+7일");
    }

    @Test
    void rejectsOverMaxDuration() {
        assertThatThrownBy(() -> policy.validate(slot(1, "09:00", "13:30"), NOW))
                .isInstanceOf(BusinessException.class).hasMessageContaining("최대 4시간");
    }

    @Test
    void rejectsOutsideOperatingHoursOrOffSlot() {
        assertThatThrownBy(() -> policy.validate(slot(1, "08:30", "10:00"), NOW))
                .isInstanceOf(BusinessException.class).hasMessageContaining("09:00~22:00");
        assertThatThrownBy(() -> policy.validate(slot(1, "21:00", "22:30"), NOW))
                .isInstanceOf(BusinessException.class).hasMessageContaining("09:00~22:00");
        assertThatThrownBy(() -> policy.validate(slot(1, "10:15", "11:00"), NOW))
                .isInstanceOf(BusinessException.class).hasMessageContaining("30분 단위");
    }

    @Test
    void rejectsStartAtOrBeforeNow() {
        assertThatThrownBy(() -> policy.validate(slot(0, "10:00", "11:00"), NOW))
                .isInstanceOf(BusinessException.class).hasMessageContaining("지난 시간");
    }

    @Test
    void bindsFromPropertiesWithoutHardcodedValues() {
        Map<String, String> props = Map.of(
                "app.reservation.policy.max-advance-days", "1",
                "app.reservation.policy.max-reservation-hours", "3",
                "app.reservation.policy.open-time", "08:00",
                "app.reservation.policy.close-time", "23:00",
                "app.reservation.policy.slot-minutes", "60");
        ReservationPolicyProperties bound = new Binder(new MapConfigurationPropertySource(props))
                .bind("app.reservation.policy", ReservationPolicyProperties.class).get();

        assertThat(bound.toPolicy())
                .isEqualTo(new ReservationPolicy(1, 3, LocalTime.of(8, 0), LocalTime.of(23, 0), 60));
    }
}
