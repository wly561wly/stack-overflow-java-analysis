package org.example.stackoverflowjavaanalysis.controller;

import org.example.stackoverflowjavaanalysis.data.model.Topic;
import org.example.stackoverflowjavaanalysis.service.MultithreadingPitfallService;
import org.example.stackoverflowjavaanalysis.service.SolvableQuestionService;
import org.example.stackoverflowjavaanalysis.service.TopicCooccurrenceService;
import org.example.stackoverflowjavaanalysis.service.TopicService;
import org.example.stackoverflowjavaanalysis.service.TopicTrendService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class AnalysisApiController {

    @Autowired
    private TopicCooccurrenceService cooccurrenceService;

    @Autowired
    private TopicTrendService trendService;

    @Autowired
    private SolvableQuestionService solvableService;

    @Autowired
    private MultithreadingPitfallService pitfallService;

    @Autowired
    private TopicService topicService;

    private final String DEFAULT_START_DATE = "2008-01-01";

    @GetMapping("/cooccurrence")
    public ResponseEntity<Map<String, Object>> getCooccurrence(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "10") int topN,
            @RequestParam(defaultValue = "count") String metric) {
        
        String start = startDate != null ? startDate : DEFAULT_START_DATE;
        String end = endDate != null ? endDate : LocalDate.now().toString();
        
        return ResponseEntity.ok(cooccurrenceService.getCooccurrenceData(start, end, topN, metric));
    }

    @GetMapping("/trend")
    public ResponseEntity<Map<String, Object>> getTrend(
            @RequestParam(required = false) List<Long> topicIds,
            @RequestParam(required = false) List<String> keywords,
            @RequestParam(defaultValue = "tag,title,fulltext") List<String> scopes,
            @RequestParam(defaultValue = "line") String chartType,
            @RequestParam(defaultValue = "separate") String mode,
            @RequestParam(defaultValue = "count") String fixedMetric,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "quarter") String granularity) {

        String start = startDate != null ? startDate : DEFAULT_START_DATE;
        String end = endDate != null ? endDate : LocalDate.now().toString();

        List<Long> ids = topicIds;
        if (ids == null || ids.isEmpty()) {
            ids = topicService.getAllTopics().stream().map(Topic::getId).collect(Collectors.toList());
        }

        return ResponseEntity.ok(trendService.getTrendData(ids, keywords, scopes, chartType, mode, fixedMetric, start, end, granularity));
    }

    @GetMapping("/solvable")
    public ResponseEntity<Map<String, Object>> getSolvable(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long topicId) {

        String start = startDate != null ? startDate : DEFAULT_START_DATE;
        String end = endDate != null ? endDate : LocalDate.now().toString();

        return ResponseEntity.ok(solvableService.getComparisonData(start, end, topicId));
    }

    @GetMapping("/pitfall")
    public ResponseEntity<Map<String, Object>> getPitfall(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) List<String> pitfalls,
            @RequestParam(required = false) List<String> customWords,
            @RequestParam(defaultValue = "count") String lineChartAttribute) {

        String start = startDate != null ? startDate : DEFAULT_START_DATE;
        String end = endDate != null ? endDate : LocalDate.now().toString();
        
        List<String> selectedPitfalls = pitfalls;
        if (selectedPitfalls == null || selectedPitfalls.isEmpty()) {
            selectedPitfalls = Arrays.asList("Deadlock", "Race Condition", "Thread Safety", "Memory Visibility", "Performance", "Thread Pool", "Exception Handling");
        }

        return ResponseEntity.ok(pitfallService.getPitfallData(start, end, selectedPitfalls, customWords, lineChartAttribute));
    }

    @GetMapping("/topics")
    public ResponseEntity<List<Map<String, Object>>> getAllTopics() {
        List<Topic> topics = topicService.getAllTopics();
        List<Map<String, Object>> out = topics.stream().map(t -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", t.getId());
            m.put("name", t.getName());
            String raw = t.getRelatedKeywords();
            List<String> related = (raw == null || raw.trim().isEmpty()) ? Collections.emptyList()
                    : Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
            m.put("relatedKeywords", related);
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(out);
    }
}
