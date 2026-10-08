package com.flash.sale.api;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication; // Add this import

@SpringBootApplication
public class ApiApplication {
    public static void main(String[] args) {
        // Fix the PostgreSQL TimeZone mismatch
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata")); 
        
        SpringApplication.run(ApiApplication.class, args);
    }
}