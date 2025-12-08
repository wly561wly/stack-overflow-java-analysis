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
            // 仅初始化 Topic 基础数据，不会触发网络抓取
            topicService.initDefaultTopics();
            System.out.println("应用已启动，使用现有数据库数据。请访问 http://localhost:8080");
        };
    }
}
