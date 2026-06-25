package com.jiang;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JiangIAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(JiangIAgentApplication.class, args);
    }

}
