package org.example.stackoverflowjavaanalysis.controller;

import org.example.stackoverflowjavaanalysis.data.model.Topic;
import org.example.stackoverflowjavaanalysis.service.TopicCooccurrenceService;
import org.example.stackoverflowjavaanalysis.service.TopicService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/cooccurrence")
public class TopicCooccurrenceController {

    private static final Logger logger = LoggerFactory.getLogger(TopicCooccurrenceController.class);

    @Autowired
    private TopicCooccurrenceService cooccurrenceService;

    @Autowired
    private TopicService topicService;

    @GetMapping
    public String page() {
        return "topic-cooccurrence";
    }

    @PostMapping("/data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getData(
            @RequestParam(defaultValue = "2020-01-01") String startDate,
            @RequestParam(defaultValue = "2024-12-31") String endDate,
            @RequestParam(defaultValue = "10") int topN,
            @RequestParam(defaultValue = "count") String metric // count or heat
    ) {
        try {
            logger.info("获取共现数据: start={}, end={}, metric={}", startDate, endDate, metric);
            Map<String, Object> data = cooccurrenceService.getCooccurrenceData(startDate, endDate, topN, metric);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            logger.error("共现分析失败", e);
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.status(500).body(err);
        }
    }

    /**
     * 获取所有话题列表
     */
    @GetMapping("/topics")
    @ResponseBody
    public ResponseEntity<List<Topic>> getAllTopics() {
        try {
            List<Topic> topics = topicService.getAllTopics();
            return ResponseEntity.ok(topics);
        } catch (Exception e) {
            logger.error("获取话题列表失败", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 获取指定话题与其他话题的关联度数据
     */
    @PostMapping("/specific-topic-data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getSpecificTopicData(
            @RequestParam(defaultValue = "2020-01-01") String startDate,
            @RequestParam(defaultValue = "2024-12-31") String endDate,
            @RequestParam(defaultValue = "10") int topN,
            @RequestParam(defaultValue = "count") String metric,
            @RequestParam(required = false) Long topicId
    ) {
        try {
            logger.info("获取指定话题关联数据: start={}, end={}, metric={}, topicId={}", startDate, endDate, metric, topicId);
            Map<String, Object> data = cooccurrenceService.getSpecificTopicCooccurrenceData(startDate, endDate, topN, metric, topicId);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            logger.error("指定话题关联分析失败", e);
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.status(500).body(err);
        }
    }
}