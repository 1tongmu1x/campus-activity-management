package com.campus.activity.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CampusClock {

    @Bean
    Clock activityClock() {
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }
}
