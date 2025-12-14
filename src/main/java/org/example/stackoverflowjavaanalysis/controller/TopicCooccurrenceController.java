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

    // 原有接口不变
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
            @RequestParam(defaultValue = "count") String metric
    ) {
        try {
            logger.info("Get cooccurrence data: start={}, end={}, topN={}, metric={}", startDate, endDate, topN, metric);
            Map<String, Object> data = cooccurrenceService.getCooccurrenceData(startDate, endDate, topN, metric);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            logger.error("Cooccurrence analysis failed", e);
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.status(500).body(err);
        }
    }

    @GetMapping("/topics")
    @ResponseBody
    public ResponseEntity<List<Topic>> getAllTopics() {
        try {
            List<Topic> topics = topicService.getAllTopics();
            return ResponseEntity.ok(topics);
        } catch (Exception e) {
            logger.error("Failed to get topics list", e);
            return ResponseEntity.status(500).build();
        }
    }

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
            logger.info("Get specific-topic cooccurrence data: start={}, end={}, metric={}, topicId={}", startDate, endDate, metric, topicId);
            Map<String, Object> data = cooccurrenceService.getSpecificTopicCooccurrenceData(startDate, endDate, topN, metric, topicId);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            logger.error("Specific-topic cooccurrence analysis failed", e);
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.status(500).body(err);
        }
    }

    // 新增：获取雷达图数据接口
    @PostMapping("/radar-data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getRadarData(
            @RequestParam Long mainTopicId,       // 主话题ID
            @RequestParam Long relatedTopicId,    // 关联话题ID
            @RequestParam(defaultValue = "2020-01-01") String startDate,
            @RequestParam(defaultValue = "2024-12-31") String endDate
    ) {
        try {
            logger.info("Get radar data: mainTopicId={}, relatedTopicId={}, start={}, end={}",
                    mainTopicId, relatedTopicId, startDate, endDate);
            // 调用Service获取五维指标数据
            Map<String, Object> radarData = cooccurrenceService.getRadarData(mainTopicId, relatedTopicId, startDate, endDate);
            return ResponseEntity.ok(radarData);
        } catch (Exception e) {
            logger.error("Radar data analysis failed", e);
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.status(500).body(err);
        }
    }

}