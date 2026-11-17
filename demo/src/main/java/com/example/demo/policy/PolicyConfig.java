package com.example.demo.policy;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ReservationPolicyProperties.class)
public class PolicyConfig {
}
