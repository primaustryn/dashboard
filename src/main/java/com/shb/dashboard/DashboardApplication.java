package com.shb.dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DashboardApplication {

	/** Launches the Spring Boot application context and the embedded servlet container. */
	public static void main(String[] args) {
		SpringApplication.run(DashboardApplication.class, args);
	}

}
