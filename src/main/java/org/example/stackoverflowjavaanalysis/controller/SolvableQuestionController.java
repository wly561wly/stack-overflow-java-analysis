package org.example.stackoverflowjavaanalysis.controller;

import org.example.stackoverflowjavaanalysis.service.SolvableQuestionService;
import org.example.stackoverflowjavaanalysis.service.TopicService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/solvable")
public class SolvableQuestionController {

    private static final Logger logger = LoggerFactory.getLogger(SolvableQuestionController.class);

    @Autowired
    private SolvableQuestionService solvableService;
    
    @Autowired
    private TopicService topicService;

    @GetMapping
    public String page(Model model) {
        model.addAttribute("topics", topicService.getAllTopics());
        return "solvable-analysis";
    }

    @PostMapping("/data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getData(
            @RequestParam(defaultValue = "2020-01-01") String startDate,
            @RequestParam(defaultValue = "2024-12-31") String endDate,
            @RequestParam(required = false) Long topicId
    ) {
        try {
            logger.info("获取可解决性数据: start={}, end={}, topicId={}", startDate, endDate, topicId);
            Map<String, Object> data = solvableService.getComparisonData(startDate, endDate, topicId);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            logger.error("可解决性分析失败", e);
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.status(500).body(err);
        }
    }
}