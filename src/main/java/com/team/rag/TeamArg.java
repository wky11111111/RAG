package com.team.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TeamArg {

    public static void main(String[] args) {
        SpringApplication.run(TeamArg.class, args);
    }
}
