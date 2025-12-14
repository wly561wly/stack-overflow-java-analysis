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
            @RequestParam(required = false) String fixedMetric, // 新增固定指标参数
            @RequestParam(defaultValue = "2020-01-01") String startDate,
            @RequestParam(defaultValue = "2024-12-31") String endDate) {

        Map<String, Object> error = new HashMap<>();

        // 1. 校验搜索范围
            if (scopes == null || scopes.trim().isEmpty()) {
                error.put("error", "Please select at least one scope (tag,title,fulltext)");
            return ResponseEntity.badRequest().body(error);
        }
        List<String> scopeList = Arrays.stream(scopes.split(",")).map(String::trim).collect(Collectors.toList());

        // 2. 校验图表与模式
            if ("pie".equalsIgnoreCase(chartType) && "integrate".equalsIgnoreCase(mode)) {
                error.put("error", "Pie chart does not support integrate mode. Use compare mode.");
            return ResponseEntity.badRequest().body(error);
        }

        // 3. 校验对比数量
            if (("compare".equalsIgnoreCase(mode) || "fixedMetric".equalsIgnoreCase(mode)) && topicIds.size() > 3) {
                error.put("error", "Compare/fixedMetric mode supports at most 3 topics.");
            return ResponseEntity.badRequest().body(error);
        }

        try {
            List<String> keywordList = (selectedKeywords == null || selectedKeywords.isBlank())
                    ? new ArrayList<>()
                    : Arrays.stream(selectedKeywords.split(",")).map(String::trim).collect(Collectors.toList());

                logger.info("Query trend data: start={}, end={}, topicIds={}, keywordsCount={}, scopes={}, chartType={}, mode={}, fixedMetric={}",
                    startDate, endDate, topicIds, keywordList.size(), scopeList, chartType, mode, fixedMetric);

            Map<String, Object> result = topicTrendService.getTrendData(
                    topicIds, keywordList, scopeList, chartType, mode, fixedMetric, startDate, endDate // 传递fixedMetric参数
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
                logger.error("Error generating trend data", e);
                error.put("error", "Internal server error: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
}