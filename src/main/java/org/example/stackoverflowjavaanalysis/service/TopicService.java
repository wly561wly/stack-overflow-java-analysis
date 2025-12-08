package org.example.stackoverflowjavaanalysis.service;

import org.example.stackoverflowjavaanalysis.data.model.Topic;
import org.example.stackoverflowjavaanalysis.data.repository.TopicRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TopicService {
    @Autowired
    private TopicRepository topicRepository;

    @Transactional
    public void initDefaultTopics() {
        // 格式: {name, description, keywords}
        String[][] defaultTopics = {
                {"java", "Java 语言与生态", "jvm,jdk,garbage-collector,lambda,streams"},
                {"multithreading", "并发与多线程", "thread,concurrency,lock,synchronized,atomic"},
                {"spring-boot", "Spring Boot 框架", "spring,spring-boot,dependency-injection,autoconfigure,aop"},
                {"spring", "Spring 框架", "spring,beans,context,annotation,ioc"},
                {"jpa", "JPA / Hibernate", "jpa,hibernate,entity,repository,transaction"},
                {"collections", "集合与数据结构", "list,map,set,arraylist,hashmap"},
                {"streams", "Stream / 函数式", "stream,lambda,collectors,optional"},
                {"io", "I/O 与 NIO", "io,nio,file,stream,buffered"},
                {"concurrency", "并发工具与包", "executor,threadpool,future,volatile,locks"},
                {"generics", "泛型", "generics,type-erasure,wildcard"},
                {"jvm", "JVM 调优与类加载", "jvm,garbage-collector,classloader,performance"},
                {"performance", "性能调优", "profiling,benchmark,optimization,gc"},
                {"testing", "测试相关", "junit,mockito,integration-test,unit-test"},
                {"security", "安全", "security,oauth,jwt,authentication,authorization"}
        };

        for (String[] topicArr : defaultTopics) {
            String name = topicArr[0];
            String desc = topicArr[1];
            String keywords = topicArr[2];

            Topic topic = topicRepository.findByName(name).orElse(new Topic());
            topic.setName(name);
            topic.setDescription(desc);
            topic.setRelatedKeywords(keywords);
            topicRepository.save(topic);
        }
    }

    public List<Topic> getAllTopics() {
        return topicRepository.findAll();
    }
}