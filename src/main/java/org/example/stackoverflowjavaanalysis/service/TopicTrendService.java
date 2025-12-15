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
import java.time.temporal.IsoFields;
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
                                            String fixedMetric,
                                            String startDateStr,
                                            String endDateStr,
                                            String granularity) {
        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        Map<Long, List<String>> topicEffectiveKeywords = new LinkedHashMap<>();

        // 假设这里有 topics 列表（按 topicIds 或全部加载）
        List<Topic> topics = (topicIds == null || topicIds.isEmpty())
                ? topicRepository.findAll()
                : topicRepository.findAllById(topicIds);

        for (Topic t : topics) {
            String rawRelated = t.getRelatedKeywords();
            List<String> related = (rawRelated == null || rawRelated.trim().isEmpty())
                    ? Collections.emptyList()
                    : Arrays.stream(rawRelated.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            Set<String> effective = new LinkedHashSet<>();

            if (selectedKeywords != null && !selectedKeywords.isEmpty()) {
                // 交集优先
                for (String kw : related) {
                    if (selectedKeywords.contains(kw)) effective.add(kw);
                }
                // 如果交集为空，回退：使用 topic 名与 relatedKeywords
                if (effective.isEmpty()) {
                    effective.add(t.getName());
                    effective.addAll(related);
                } else {
                    // 始终保证包含 topic 名
                    effective.add(t.getName());
                }
            } else {
                // 未传 selectedKeywords，使用 topic 名 + related
                effective.add(t.getName());
                effective.addAll(related);
            }

            List<String> effectiveList = new ArrayList<>(effective);
            topicEffectiveKeywords.put(t.getId(), effectiveList);

            // 用 effectiveList 去查询并统计（示例）
            // Set<Question> questions = queryQuestions(effectiveList, scopes, start, end);
            // logger.debug("Topic [{}] effectiveKeywords={} => questionsFound={}", t.getName(), effectiveList, questions.size());
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
            if ("pie".equalsIgnoreCase(chartType)) {
                return buildPieData(Map.of("ALL", allQuestions));
            } else {
                // 传递 granularity
                return buildTimeSeriesDataWithNormalization(Map.of("ALL", allQuestions), chartType, granularity);
            }

        } else if ("fixedMetric".equalsIgnoreCase(mode)) {
            // 固定指标模式：只展示指定指标的数据
            Map<String, Set<Question>> topicQuestionsMap = new LinkedHashMap<>();
            for (Topic t : topics) {
                List<String> kws = topicEffectiveKeywords.get(t.getId());
                if (kws != null && !kws.isEmpty()) {
                    Set<Question> qs = queryQuestions(kws, scopes, startDateTime, endDateTime);
                    logger.debug("Topic {} -> keywords={} -> questionsFound={}", t.getName(), kws, qs.size());
                    topicQuestionsMap.put(t.getName(), qs);
                } else {
                    logger.debug("Topic {} has no keywords after processing, returning empty set", t.getName());
                    topicQuestionsMap.put(t.getName(), new HashSet<>());
                }
            }

            if ("pie".equalsIgnoreCase(chartType)) {
                return buildPieData(topicQuestionsMap);
            } else {
                // 传递 granularity
                return buildTimeSeriesDataWithFixedMetric(topicQuestionsMap, chartType, fixedMetric, granularity);
            }
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
                // 传递 granularity
                return buildTimeSeriesDataWithNormalization(topicQuestionsMap, chartType, granularity);
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

    private List<Double> normalize(List<Number> values) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        List<Double> doubleValues = values.stream()
                .map(Number::doubleValue)
                .collect(Collectors.toList());
        double min = Collections.min(doubleValues);
        double max = Collections.max(doubleValues);
        if (max == min) {
            return doubleValues.stream().map(v -> 0.0).collect(Collectors.toList());
        }
        return doubleValues.stream()
                .map(v -> (v - min) / (max - min))
                .collect(Collectors.toList());
    }

    // 新增：根据粒度格式化日期键
    private String formatGroupKey(LocalDateTime date, String granularity) {
        if ("year".equalsIgnoreCase(granularity)) {
            return date.format(DateTimeFormatter.ofPattern("yyyy"));
        } else if ("month".equalsIgnoreCase(granularity)) {
            return date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        } else {
            // 默认为季度 (quarter)
            int year = date.getYear();
            int quarter = date.get(IsoFields.QUARTER_OF_YEAR);
            return year + "-Q" + quarter;
        }
    }

    private Map<String, Object> buildTimeSeriesDataWithFixedMetric(Map<String, Set<Question>> dataMap, String chartType, String fixedMetric, String granularity) {
        SortedSet<String> dates = new TreeSet<>();
        Map<String, Map<String, List<Question>>> groupedData = new HashMap<>();

        // 1. Group by Date using granularity
        for (String key : dataMap.keySet()) {
            Map<String, List<Question>> byDate = dataMap.get(key).stream()
                    .collect(Collectors.groupingBy(q -> formatGroupKey(q.getCreationDate(), granularity)));
            groupedData.put(key, byDate);
            dates.addAll(byDate.keySet());
        }

        // 2. Build Series
        Map<String, List<Number>> fixedMetricSeries = new LinkedHashMap<>();
        for (String key : dataMap.keySet()) {
            fixedMetricSeries.put(key, new ArrayList<>());
        }

        for (String date : dates) {
            for (String key : dataMap.keySet()) {
                List<Question> list = groupedData.get(key).getOrDefault(date, Collections.emptyList());

                // 根据固定指标选择相应的数据
                switch (fixedMetric) {
                    case "count":
                        fixedMetricSeries.get(key).add(list.size());
                        break;
                    case "score":
                        fixedMetricSeries.get(key).add(list.stream().mapToLong(q -> q.getScore() == null ? 0 : q.getScore()).sum());
                        break;
                    case "views":
                        fixedMetricSeries.get(key).add(list.stream().mapToLong(q -> q.getViewCount() == null ? 0 : q.getViewCount()).sum());
                        break;
                    case "answers":
                        fixedMetricSeries.get(key).add(list.stream().mapToLong(q -> q.getAnswerCount() == null ? 0 : q.getAnswerCount()).sum());
                        break;
                    default:
                        fixedMetricSeries.get(key).add(list.size());
                        break;
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("dates", dates);
        result.put("fixedMetricSeries", fixedMetricSeries);  // 固定指标数据
        return result;
    }

    // 新增辅助方法：基于指定的全局 Min/Max 进行归一化
    private List<Double> normalizeWithBounds(List<Number> values, double min, double max) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        if (max == min) {
            return values.stream().map(v -> 0.0).collect(Collectors.toList());
        }
        return values.stream()
                .map(Number::doubleValue)
                .map(v -> (v - min) / (max - min))
                .collect(Collectors.toList());
    }

    // 修改此方法：只返回原始数据，移除后端归一化逻辑
    private Map<String, Object> buildTimeSeriesDataWithNormalization(Map<String, Set<Question>> dataMap, String chartType, String granularity) {
        SortedSet<String> dates = new TreeSet<>();
        Map<String, Map<String, List<Question>>> groupedData = new HashMap<>();

        // 1. Group by Date using granularity
        for (String key : dataMap.keySet()) {
            Map<String, List<Question>> byDate = dataMap.get(key).stream()
                    .collect(Collectors.groupingBy(q -> formatGroupKey(q.getCreationDate(), granularity)));
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

                int count = list.size();
                long score = list.stream().mapToLong(q -> q.getScore() == null ? 0 : q.getScore()).sum();
                long views = list.stream().mapToLong(q -> q.getViewCount() == null ? 0 : q.getViewCount()).sum();
                long answers = list.stream().mapToLong(q -> q.getAnswerCount() == null ? 0 : q.getAnswerCount()).sum();

                series.get(key + "_count").add(count);
                series.get(key + "_score").add(score);
                series.get(key + "_views").add(views);
                series.get(key + "_answers").add(answers);
            }
        }

        // --- 生成归一化结果和 seriesMap（topic -> series keys） ---
        Map<String, List<String>> seriesMap = new LinkedHashMap<>();
        for (String key : dataMap.keySet()) {
            List<String> keys = new ArrayList<>();
            keys.add(key + "_count");
            keys.add(key + "_score");
            keys.add(key + "_views");
            keys.add(key + "_answers");
            seriesMap.put(key, keys);
        }

        // 计算全局极值并归一化（后端提供的归一化，前端可以直接使用）
        List<Double> allCounts = new ArrayList<>();
        List<Double> allScores = new ArrayList<>();
        List<Double> allViews = new ArrayList<>();
        List<Double> allAnswers = new ArrayList<>();

        for (String key : dataMap.keySet()) {
            allCounts.addAll(series.get(key + "_count").stream().map(Number::doubleValue).collect(Collectors.toList()));
            allScores.addAll(series.get(key + "_score").stream().map(Number::doubleValue).collect(Collectors.toList()));
            allViews.addAll(series.get(key + "_views").stream().map(Number::doubleValue).collect(Collectors.toList()));
            allAnswers.addAll(series.get(key + "_answers").stream().map(Number::doubleValue).collect(Collectors.toList()));
        }

        double minCount = allCounts.isEmpty() ? 0 : Collections.min(allCounts);
        double maxCount = allCounts.isEmpty() ? 0 : Collections.max(allCounts);
        double minScore = allScores.isEmpty() ? 0 : Collections.min(allScores);
        double maxScore = allScores.isEmpty() ? 0 : Collections.max(allScores);
        double minViews = allViews.isEmpty() ? 0 : Collections.min(allViews);
        double maxViews = allViews.isEmpty() ? 0 : Collections.max(allViews);
        double minAnswers = allAnswers.isEmpty() ? 0 : Collections.min(allAnswers);
        double maxAnswers = allAnswers.isEmpty() ? 0 : Collections.max(allAnswers);

        Map<String, List<Double>> normalizedSeries = new LinkedHashMap<>();
        for (String key : dataMap.keySet()) {
            normalizedSeries.put(key + "_count_normalized", normalizeWithBounds(series.get(key + "_count"), minCount, maxCount));
            normalizedSeries.put(key + "_score_normalized", normalizeWithBounds(series.get(key + "_score"), minScore, maxScore));
            normalizedSeries.put(key + "_views_normalized", normalizeWithBounds(series.get(key + "_views"), minViews, maxViews));
            normalizedSeries.put(key + "_answers_normalized", normalizeWithBounds(series.get(key + "_answers"), minAnswers, maxAnswers));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("dates", dates);
        result.put("series", series);  // 原始整数数据
        result.put("normalizedSeries", normalizedSeries); // 后端归一化，0..1
        result.put("seriesMap", seriesMap); // 明确的 topic -> series keys 映射，便于前端映射
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