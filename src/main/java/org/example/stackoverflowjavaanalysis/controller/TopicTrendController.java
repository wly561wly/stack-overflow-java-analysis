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
        logger.info("正在加载趋势分析页面...");
        List<Topic> topics = topicService.getAllTopics();
        
        // 调试日志：检查数据完整性
        logger.info("加载了 {} 个主题", topics.size());
        for (Topic t : topics) {
            if (t.getRelatedKeywords() == null || t.getRelatedKeywords().isEmpty()) {
                logger.warn("警告: 主题 [ID={}, Name={}] 的关键词为空，可能导致页面显示不全", t.getId(), t.getName());
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
            @RequestParam(defaultValue = "2020-01-01") String startDate,
            @RequestParam(defaultValue = "2024-12-31") String endDate) {
        
        Map<String, Object> error = new HashMap<>();

        // 1. 校验搜索范围
        if (scopes == null || scopes.trim().isEmpty()) {
            error.put("error", "请至少选择一个搜索范围（Tag, Title, 全文）");
            return ResponseEntity.badRequest().body(error);
        }
        List<String> scopeList = Arrays.stream(scopes.split(",")).map(String::trim).collect(Collectors.toList());

        // 2. 校验图表与模式
        if ("pie".equalsIgnoreCase(chartType) && "integrate".equalsIgnoreCase(mode)) {
            error.put("error", "饼图不支持整合模式，请选择对比模式。");
            return ResponseEntity.badRequest().body(error);
        }

        // 3. 校验对比数量
        if ("compare".equalsIgnoreCase(mode) && topicIds.size() > 3) {
            error.put("error", "对比模式最多只能选择 3 个主题。");
            return ResponseEntity.badRequest().body(error);
        }

        try {
            List<String> keywordList = (selectedKeywords == null || selectedKeywords.isBlank())
                    ? new ArrayList<>()
                    : Arrays.stream(selectedKeywords.split(",")).map(String::trim).collect(Collectors.toList());

            logger.info("查询趋势数据: topicIds={}, keywordsCount={}, scopes={}, mode={}", 
                    topicIds, keywordList.size(), scopeList, mode);

            Map<String, Object> result = topicTrendService.getTrendData(
                    topicIds, keywordList, scopeList, chartType, mode, startDate, endDate
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("生成趋势数据时发生错误", e);
            error.put("error", "服务器内部错误: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
}