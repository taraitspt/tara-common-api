package com.tara.commonapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.TimeZone;

/**
 * 타라 공용 API 서버 — 여러 사내 시스템이 함께 쓰는 기능을 모은다.
 * 첫 기능: Teams 1:1 메시지 발송 API (hrm-module-api 에서 분리).
 */
@SpringBootApplication
@EnableJpaAuditing
public class TaraCommonApiApplication {

    public static void main(String[] args) {
        // 운영 서버 OS 시간대가 UTC 여도 LocalDateTime.now() 가 한국 시간이 되도록(HRM 과 동일)
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        SpringApplication.run(TaraCommonApiApplication.class, args);
    }
}
