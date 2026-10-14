package com.example.repoai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class RepoaiApplication {

	public static void main(String[] args) {
		SpringApplication.run(RepoaiApplication.class, args);
	}

}
