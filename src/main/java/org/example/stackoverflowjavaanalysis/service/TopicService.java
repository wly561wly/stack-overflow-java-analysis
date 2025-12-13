package org.example.stackoverflowjavaanalysis.service;

import org.example.stackoverflowjavaanalysis.data.model.Topic;
import org.example.stackoverflowjavaanalysis.data.repository.TopicRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class TopicService {
    @Autowired
    private TopicRepository topicRepository;

    @Transactional
    public void initDefaultTopics() {
        // 格式: {name, description, keywords}
        // 这里是你优化后的列表，确保子内容不重复
        String[][] defaultTopics = {
            {"java", "Java 语言核心与语法", "jdk,language,syntax,generics,annotations"},
            {"concurrency", "并发与多线程", "thread,concurrency,deadlock,race-condition,volatile,synchronized,atomic,locks,executor,threadpool"},
            {"spring", "Spring 生态（含 Boot）", "spring,spring-boot,dependency-injection,aop,autoconfigure,beans,context"},
            {"jvm", "JVM、类加载与性能", "jvm,classloader,garbage-collector,hotspot,performance,jvm-tuning"},
            {"persistence", "数据持久化（JPA / SQL）", "jpa,hibernate,sql,entity,repository,transaction"},
            {"collections", "集合与数据结构", "list,map,set,arraylist,hashmap,deque"},
            {"streams", "Stream 与函数式编程", "stream,lambda,collectors,optional,functional"},
            {"io", "I/O 与网络", "io,nio,file,buffered,network,socket"},
            {"testing", "测试与质量保障", "junit,mockito,integration-test,unit-test,coverage"},
            {"security", "安全与认证授权", "security,oauth,jwt,authentication,authorization,encryption"}
        };

        // 1. 收集当前代码中定义的所有 Topic 名称
        Set<String> validTopicNames = new HashSet<>();

        // 2. 更新或创建定义的 Topic
        for (String[] topicArr : defaultTopics) {
            String name = topicArr[0];
            String desc = topicArr[1];
            String keywords = topicArr[2];

            validTopicNames.add(name);

            Topic topic = topicRepository.findByName(name).orElse(new Topic());
            topic.setName(name);
            topic.setDescription(desc);
            topic.setRelatedKeywords(keywords);
            topicRepository.save(topic);
        }

        // 3. 【新增】清理数据库中存在、但不再 defaultTopics 列表中的旧 Topic
        // 这样可以确保页面只显示代码里配置的那 10 个主题
        List<Topic> allTopics = topicRepository.findAll();
        for (Topic t : allTopics) {
            if (!validTopicNames.contains(t.getName())) {
                // 比如旧的 "lambda" 主题，名字不在 validTopicNames 里，就会被删掉
                topicRepository.delete(t);
            }
        }
    }

    public List<Topic> getAllTopics() {
        return topicRepository.findAll();
    }
}