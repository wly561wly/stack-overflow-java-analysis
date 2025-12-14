package org.example.stackoverflowjavaanalysis;

import org.example.stackoverflowjavaanalysis.service.TopicService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class StackOverflowJavaAnalysisApplication {

    public static void main(String[] args) {
        SpringApplication.run(StackOverflowJavaAnalysisApplication.class, args);
    }

    @Bean
    CommandLineRunner initTopics(TopicService topicService) {
        return args -> {
            // initialize default Topic data only (no network fetch)
            topicService.initDefaultTopics();
            System.out.println("Application started. Using existing database data. Visit http://localhost:8080");
        };
    }
}
