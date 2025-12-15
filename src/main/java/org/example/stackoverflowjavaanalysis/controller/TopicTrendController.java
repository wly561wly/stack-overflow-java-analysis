package org.example.stackoverflowjavaanalysis.controller;

import org.example.stackoverflowjavaanalysis.data.model.Topic; // 引入 Topic 类
import org.example.stackoverflowjavaanalysis.service.TopicService;
import org.example.stackoverflowjavaanalysis.service.TopicTrendService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/trend")
public class TopicTrendController {

    private static final Logger logger = LoggerFactory.getLogger(TopicTrendController.class);

    @Autowired
    private TopicService topicService;
    @Autowired
    private TopicTrendService topicTrendService;

    @GetMapping
    public String trendPage(Model model) {
        logger.info("Loading trend analysis page...");
        List<Topic> topics = topicService.getAllTopics();

        // 调试日志：检查数据完整性
        logger.info("Loaded {} topics", topics.size());
        for (Topic t : topics) {
            if (t.getRelatedKeywords() == null || t.getRelatedKeywords().isEmpty()) {
                logger.warn("Warning: topic [ID={}, Name={}] has empty relatedKeywords", t.getId(), t.getName());
            }
        }

        model.addAttribute("topics", topics);
        return "topic-trend";
    }

    @PostMapping("/data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getTrendData(
            @RequestParam List<Long> topicIds,
            @RequestParam(required = false) String selectedKeywords,
            @RequestParam(required = false) String scopes,
            @RequestParam(defaultValue = "line") String chartType,
            @RequestParam(defaultValue = "compare") String mode,
            @RequestParam(required = false) String fixedMetric,
            @RequestParam(defaultValue = "quarter") String granularity,
            @RequestParam(defaultValue = "2020-01-01") String startDate,
            @RequestParam(defaultValue = "2024-12-31") String endDate) {

        // 将逗号分隔的 String 转为 List<String>
        List<String> selectedKeywordList = (selectedKeywords == null || selectedKeywords.trim().isEmpty())
                ? Collections.emptyList()
                : Arrays.stream(selectedKeywords.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());

        List<String> scopesList = (scopes == null || scopes.trim().isEmpty())
                ? Arrays.asList("tag", "title", "fulltext")
                : Arrays.stream(scopes.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());

        Map<String, Object> result = topicTrendService.getTrendData(
                topicIds, selectedKeywordList, scopesList,
                chartType, mode, fixedMetric, startDate, endDate, granularity);

        return ResponseEntity.ok(result);
    }
}