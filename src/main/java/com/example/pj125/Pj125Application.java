package com.example.pj125;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Pj125Application {
    public static void main(String[] args) {
        SpringApplication.run(Pj125Application.class, args);
    }
}
