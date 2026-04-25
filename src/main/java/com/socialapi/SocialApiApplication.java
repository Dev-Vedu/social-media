package com.socialapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling  // needed for the CRON sweeper in Phase 3
public class SocialApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocialApiApplication.class, args);
    }
}
