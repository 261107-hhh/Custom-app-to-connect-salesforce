package com.salesforce.sync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SalesforceSyncApplication {

    public static void main(String[] args) {
        SpringApplication.run(SalesforceSyncApplication.class, args);
    }
}
