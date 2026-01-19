package com.ezh.ezauth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class EzauthApplication {

	public static void main(String[] args) {
		SpringApplication.run(EzauthApplication.class, args);
	}
}
