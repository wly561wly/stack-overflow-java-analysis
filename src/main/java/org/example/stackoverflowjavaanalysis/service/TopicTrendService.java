package org.example.stackoverflowjavaanalysis.service;

import org.example.stackoverflowjavaanalysis.data.model.Question;
import org.example.stackoverflowjavaanalysis.data.model.Topic;
import org.example.stackoverflowjavaanalysis.data.repository.QuestionRepository;
import org.example.stackoverflowjavaanalysis.data.repository.TopicRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TopicTrendService {

    private static final Logger logger = LoggerFactory.getLogger(TopicTrendService.class);

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private TopicRepository topicRepository;

    public Map<String, Object> getTrendData(List<Long> topicIds,
                                            List<String> selectedKeywords,
                                            List<String> scopes,
                                            String chartType,
                                            String mode,
                                            String startDateStr,
                                            String endDateStr) {
        
        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        List<Topic> topics = topicRepository.findAllById(topicIds);
        
        // 准备每个 Topic 的有效关键词
        // 逻辑：如果前端传了 selectedKeywords，则取 Topic 自身关键词与 selectedKeywords 的交集
        // 如果交集为空（说明该 Topic 下没有勾选任何词），则该 Topic 不应有数据（或者前端已控制不传该ID）
        Map<Long, List<String>> topicEffectiveKeywords = new HashMap<>();
        for (Topic t : topics) {
            List<String> topicKws = Arrays.asList(t.getRelatedKeywords().split(","));
            List<String> effective = new ArrayList<>();
            for (String kw : topicKws) {
                if (selectedKeywords.contains(kw.trim())) {
                    effective.add(kw.trim());
                }
            }
            // 如果前端没传任何 keyword (异常情况)，或者交集为空，兜底使用 topic name
            if (effective.isEmpty() && (selectedKeywords == null || selectedKeywords.isEmpty())) {
                effective.add(t.getName());
            }
            topicEffectiveKeywords.put(t.getId(), effective);
        }

        if ("integrate".equalsIgnoreCase(mode)) {
            // 整合模式：合并所有 Topic 的问题，去重
            Set<Question> allQuestions = new HashSet<>();
            for (Topic t : topics) {
                List<String> kws = topicEffectiveKeywords.get(t.getId());
                if (kws != null && !kws.isEmpty()) {
                    allQuestions.addAll(queryQuestions(kws, scopes, startDateTime, endDateTime));
                }
            }
            // 整合模式只有一个 "ALL" 系列
            return buildTimeSeriesData(Map.of("ALL", allQuestions), chartType);

        } else {
            // 对比模式：每个 Topic 独立统计
            Map<String, Set<Question>> topicQuestionsMap = new LinkedHashMap<>();
            for (Topic t : topics) {
                List<String> kws = topicEffectiveKeywords.get(t.getId());
                if (kws != null && !kws.isEmpty()) {
                    Set<Question> qs = queryQuestions(kws, scopes, startDateTime, endDateTime);
                    topicQuestionsMap.put(t.getName(), qs);
                } else {
                    topicQuestionsMap.put(t.getName(), new HashSet<>());
                }
            }

            if ("pie".equalsIgnoreCase(chartType)) {
                return buildPieData(topicQuestionsMap);
            } else {
                return buildTimeSeriesData(topicQuestionsMap, chartType);
            }
        }
    }

    private Set<Question> queryQuestions(List<String> keywords, List<String> scopes, LocalDateTime start, LocalDateTime end) {
        Set<Question> result = new HashSet<>();
        for (String kw : keywords) {
            if (scopes.contains("tag")) {
                result.addAll(questionRepository.findByTagsContainingAndCreationDateBetween(kw, start, end));
            }
            if (scopes.contains("title")) {
                result.addAll(questionRepository.findByTitleContainingAndCreationDateBetween(kw, start, end));
            }
            if (scopes.contains("fulltext")) {
                result.addAll(questionRepository.findByBodyContainingAndCreationDateBetween(kw, start, end));
            }
        }
        return result;
    }

    private Map<String, Object> buildTimeSeriesData(Map<String, Set<Question>> dataMap, String chartType) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        SortedSet<String> dates = new TreeSet<>();
        Map<String, Map<String, List<Question>>> groupedData = new HashMap<>();

        // 1. Group by Date
        for (String key : dataMap.keySet()) {
            Map<String, List<Question>> byDate = dataMap.get(key).stream()
                    .collect(Collectors.groupingBy(q -> q.getCreationDate().format(fmt)));
            groupedData.put(key, byDate);
            dates.addAll(byDate.keySet());
        }

        // 2. Build Series
        Map<String, List<Number>> series = new LinkedHashMap<>();
        for (String key : dataMap.keySet()) {
            series.put(key + "_count", new ArrayList<>());
            series.put(key + "_score", new ArrayList<>());
            series.put(key + "_views", new ArrayList<>());
            series.put(key + "_answers", new ArrayList<>());
        }

        for (String date : dates) {
            for (String key : dataMap.keySet()) {
                List<Question> list = groupedData.get(key).getOrDefault(date, Collections.emptyList());
                
                series.get(key + "_count").add(list.size());
                series.get(key + "_score").add(list.stream().mapToLong(q -> q.getScore() == null ? 0 : q.getScore()).sum());
                series.get(key + "_views").add(list.stream().mapToLong(q -> q.getViewCount() == null ? 0 : q.getViewCount()).sum());
                series.get(key + "_answers").add(list.stream().mapToLong(q -> q.getAnswerCount() == null ? 0 : q.getAnswerCount()).sum());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("dates", dates);
        result.put("series", series);
        return result;
    }

    private Map<String, Object> buildPieData(Map<String, Set<Question>> dataMap) {
        Map<String, Object> pies = new HashMap<>();
        Map<String, Long> qCount = new HashMap<>();
        Map<String, Long> vCount = new HashMap<>();
        Map<String, Long> aCount = new HashMap<>();
        Map<String, Long> sCount = new HashMap<>();

        for (String key : dataMap.keySet()) {
            Set<Question> list = dataMap.get(key);
            qCount.put(key, (long) list.size());
            vCount.put(key, list.stream().mapToLong(q -> q.getViewCount() == null ? 0 : q.getViewCount()).sum());
            aCount.put(key, list.stream().mapToLong(q -> q.getAnswerCount() == null ? 0 : q.getAnswerCount()).sum());
            sCount.put(key, list.stream().mapToLong(q -> q.getScore() == null ? 0 : q.getScore()).sum());
        }

        pies.put("questionCount", qCount);
        pies.put("viewCount", vCount);
        pies.put("answerCount", aCount);
        pies.put("score", sCount);

        Map<String, Object> result = new HashMap<>();
        result.put("pies", pies);
        return result;
    }
}