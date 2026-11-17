package com.example.demo.common;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 현재 시각 주입용 Clock 빈 (설계서 p.4). 테스트에서는 Clock.fixed(...)로 대체한다. */
@Configuration
public class TimeConfig {
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(ZONE);
    }
}
