package org.example.stackoverflowjavaanalysis.controller;

import org.example.stackoverflowjavaanalysis.service.TopicCooccurrenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/cooccurrence")
public class TopicCooccurrenceController {

    private static final Logger logger = LoggerFactory.getLogger(TopicCooccurrenceController.class);

    @Autowired
    private TopicCooccurrenceService cooccurrenceService;

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
}