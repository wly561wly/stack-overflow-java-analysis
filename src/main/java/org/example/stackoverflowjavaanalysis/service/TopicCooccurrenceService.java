package org.example.stackoverflowjavaanalysis.service;

import org.example.stackoverflowjavaanalysis.data.model.Question;
import org.example.stackoverflowjavaanalysis.data.model.Topic;
import org.example.stackoverflowjavaanalysis.data.repository.QuestionRepository;
import org.example.stackoverflowjavaanalysis.data.repository.TopicRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.example.stackoverflowjavaanalysis.util.NumberUtils; // 导入工具类


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TopicCooccurrenceService {

    private static final Logger logger = LoggerFactory.getLogger(TopicCooccurrenceService.class);

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private TopicRepository topicRepository;

    // 内部类：用于统计共现指标
    private static class CooccurrenceStats {
        long count = 0;
        long totalScore = 0;
        long totalViews = 0;
        long qualityCount = 0; // 假设 score > 5 为优质
        long totalAnswerCount = 0; // 新增：总回答数

        void add(Question q) {
            this.count++;
            this.totalScore += (q.getScore() != null ? q.getScore() : 0);
            this.totalViews += (q.getViewCount() != null ? q.getViewCount() : 0);
            this.totalAnswerCount += (q.getAnswerCount() != null ? q.getAnswerCount() : 0); // 新增：统计回答数量
            if (q.getScore() != null && q.getScore() > 5) {
                this.qualityCount++;
            }
        }

        double getHeat() {
            return totalScore + totalViews; // 简单热度定义
        }
    }

    public Map<String, Object> getCooccurrenceData(String startDateStr,
                                                   String endDateStr,
                                                   int topN,
                                                   String metric) {

        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        // 1. 数据库时间范围查询 (保持高效)
        List<Question> questions = questionRepository.findByCreationDateBetween(startDateTime, endDateTime);
        List<Topic> allTopics = topicRepository.findAll();

        logger.info("Cooccurrence analysis (Fast Mode): start={}, end={}, questions={}, topics={}",
                startDateStr, endDateStr, questions.size(), allTopics.size());

        // 2. 预处理 Topic 关键词 (转小写，方便后续快速匹配)
        Map<Long, List<String>> topicKeywords = new HashMap<>();
        for (Topic t : allTopics) {
            List<String> kws = new ArrayList<>();
            kws.add(t.getName().toLowerCase()); // 主名称
            if (t.getRelatedKeywords() != null && !t.getRelatedKeywords().isBlank()) {
                Arrays.stream(t.getRelatedKeywords().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(String::toLowerCase)
                        .forEach(kws::add);
            }
            topicKeywords.put(t.getId(), kws);
        }

        // 3. 遍历问题，计算共现
        Map<String, CooccurrenceStats> pairStatsMap = new HashMap<>();
        Map<Long, Integer> topicOccurrence = new HashMap<>();

        for (Question q : questions) {
            // 预处理当前问题的 Tags 和 Title (只做一次)
            String tags = q.getTags() != null ? q.getTags().toLowerCase() : "";
            String title = q.getTitle() != null ? q.getTitle().toLowerCase() : "";

            List<Long> matchedTopicIds = new ArrayList<>();

            // 简单循环匹配：检查 Tag 或 Title 是否包含关键词
            // 这种方式比正则快 10-100 倍，且不需要解析 Body
            for (Topic t : allTopics) {
                List<String> kws = topicKeywords.get(t.getId());
                boolean isMatch = false;
                for (String kw : kws) {
                    // 核心匹配逻辑：Tags 包含 OR Title 包含
                    if (tags.contains(kw) || title.contains(kw)) {
                        isMatch = true;
                        break;
                    }
                }
                if (isMatch) {
                    matchedTopicIds.add(t.getId());
                    topicOccurrence.merge(t.getId(), 1, Integer::sum);
                }
            }

            // 3.1 生成两两组合 (Pair Generation)
            if (matchedTopicIds.size() < 2) continue;

            Collections.sort(matchedTopicIds); // 排序

            for (int i = 0; i < matchedTopicIds.size(); i++) {
                for (int j = i + 1; j < matchedTopicIds.size(); j++) {
                    Long id1 = matchedTopicIds.get(i);
                    Long id2 = matchedTopicIds.get(j);
                    String key = id1 + "-" + id2;

                    pairStatsMap.computeIfAbsent(key, k -> new CooccurrenceStats()).add(q);
                }
            }
        }

        // 4. 构建图表数据 (逻辑不变)
        return buildChartData(pairStatsMap, allTopics, topicOccurrence, topN, metric);
    }

    public Map<String, Object> getSpecificTopicCooccurrenceData(String startDateStr,
                                                                String endDateStr,
                                                                int topN,
                                                                String metric,
                                                                Long specificTopicId) {
        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        // 1. 数据库时间范围查询
        List<Question> questions = questionRepository.findByCreationDateBetween(startDateTime, endDateTime);
        List<Topic> allTopics = topicRepository.findAll();

        logger.info("Specific topic analysis (Fast Mode): questions={}, topics={}, specificTopicId={}",
                questions.size(), allTopics.size(), specificTopicId);

        if (specificTopicId == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("barX", new ArrayList<>());
            result.put("barY", new ArrayList<>());
            result.put("topicName", "");
            return result;
        }

        Optional<Topic> specificTopicOpt = allTopics.stream()
                .filter(t -> t.getId().equals(specificTopicId))
                .findFirst();

        if (!specificTopicOpt.isPresent()) {
            throw new IllegalArgumentException("Specified topic ID not found: " + specificTopicId);
        }

        Topic specificTopic = specificTopicOpt.get();
        String specificTopicName = specificTopic.getName();

        // 2. 预处理 Topic 关键词
        Map<Long, List<String>> topicKeywords = new HashMap<>();
        for (Topic t : allTopics) {
            List<String> kws = new ArrayList<>();
            kws.add(t.getName().toLowerCase());
            if (t.getRelatedKeywords() != null && !t.getRelatedKeywords().isBlank()) {
                Arrays.stream(t.getRelatedKeywords().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(String::toLowerCase)
                        .forEach(kws::add);
            }
            topicKeywords.put(t.getId(), kws);
        }

        // 3. 遍历问题
        Map<Long, CooccurrenceStats> specificTopicCooccurrence = new HashMap<>();

        for (Question q : questions) {
            String tags = q.getTags() != null ? q.getTags().toLowerCase() : "";
            String title = q.getTitle() != null ? q.getTitle().toLowerCase() : "";

            List<Long> matchedTopicIds = new ArrayList<>();

            // 快速匹配逻辑
            for (Topic t : allTopics) {
                List<String> kws = topicKeywords.get(t.getId());
                boolean isMatch = false;
                for (String kw : kws) {
                    if (tags.contains(kw) || title.contains(kw)) {
                        isMatch = true;
                        break;
                    }
                }
                if (isMatch) {
                    matchedTopicIds.add(t.getId());
                }
            }

            // 4. 核心过滤：必须包含指定话题，且有其他共现话题
            if (matchedTopicIds.contains(specificTopicId) && matchedTopicIds.size() > 1) {
                for (Long topicId : matchedTopicIds) {
                    if (!topicId.equals(specificTopicId)) {
                        specificTopicCooccurrence.computeIfAbsent(topicId, k -> new CooccurrenceStats()).add(q);
                    }
                }
            }
        }

        // 5. 构建图表数据 (逻辑不变)
        return buildSpecificTopicChartData(specificTopicCooccurrence, allTopics, topN, metric, specificTopicName);
    }

    private Map<String, Object> buildChartData(Map<String, CooccurrenceStats> pairStatsMap,
                                               List<Topic> allTopics,
                                               Map<Long, Integer> topicOccurrence,
                                               int topN,
                                               String metric) {
        Map<String, Object> result = new HashMap<>();
        Map<Long, String> topicNames = allTopics.stream().collect(Collectors.toMap(Topic::getId, Topic::getName));

        // --- 1. 柱状图 (Top N) ---
        List<Map.Entry<String, CooccurrenceStats>> sortedPairs = new ArrayList<>(pairStatsMap.entrySet());
        sortedPairs.sort((e1, e2) -> {
            double v1 = "heat".equals(metric) ? e1.getValue().getHeat() : e1.getValue().count;
            double v2 = "heat".equals(metric) ? e2.getValue().getHeat() : e2.getValue().count;
            return Double.compare(v2, v1); // 降序
        });

        List<String> barX = new ArrayList<>();
        List<Double> barY = new ArrayList<>();

        int limit = Math.min(topN, sortedPairs.size());
        for (int i = 0; i < limit; i++) {
            String[] ids = sortedPairs.get(i).getKey().split("-");
            String name1 = topicNames.get(Long.parseLong(ids[0]));
            String name2 = topicNames.get(Long.parseLong(ids[1]));
            barX.add(name1 + " & " + name2);

            double val = "heat".equals(metric) ? sortedPairs.get(i).getValue().getHeat() : sortedPairs.get(i).getValue().count;
            barY.add(NumberUtils.roundToTwoDecimalPlaces(val)); // 使用NumberUtils处理
        }
        result.put("barX", barX);
        result.put("barY", barY);

        // --- 2. 热力图 (Matrix) ---
        List<Long> activeTopicIds = topicOccurrence.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(20)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<String> axisData = activeTopicIds.stream().map(topicNames::get).collect(Collectors.toList());
        List<List<Object>> heatmapData = new ArrayList<>();

        for (int i = 0; i < activeTopicIds.size(); i++) {
            for (int j = 0; j < activeTopicIds.size(); j++) {
                if (i == j) continue; // 对角线
                Long id1 = activeTopicIds.get(i);
                Long id2 = activeTopicIds.get(j);

                // 构造 Key (小-大)
                String key = (id1 < id2) ? id1 + "-" + id2 : id2 + "-" + id1;
                CooccurrenceStats stats = pairStatsMap.get(key);

                if (stats != null) {
                    double val = "heat".equals(metric) ? stats.getHeat() : stats.count;
                    // ECharts Heatmap format: [xIndex, yIndex, value]
                    heatmapData.add(Arrays.asList(i, j, NumberUtils.roundToTwoDecimalPlaces(val))); // 使用NumberUtils处理
                }
            }
        }

        result.put("heatmapAxis", axisData);
        result.put("heatmapData", heatmapData);

        return result;
    }

    private Map<String, Object> buildSpecificTopicChartData(Map<Long, CooccurrenceStats> specificTopicCooccurrence,
                                                            List<Topic> allTopics,
                                                            int topN,
                                                            String metric,
                                                            String specificTopicName) {
        Map<String, Object> result = new HashMap<>();
        Map<Long, String> topicNames = allTopics.stream().collect(Collectors.toMap(Topic::getId, Topic::getName));

        // --- 柱状图 (Top N 与指定话题关联的话题) ---
        List<Map.Entry<Long, CooccurrenceStats>> sortedEntries = new ArrayList<>(specificTopicCooccurrence.entrySet());
        sortedEntries.sort((e1, e2) -> {
            double v1 = "heat".equals(metric) ? e1.getValue().getHeat() : e1.getValue().count;
            double v2 = "heat".equals(metric) ? e2.getValue().getHeat() : e2.getValue().count;
            return Double.compare(v2, v1); // 降序
        });

        List<String> barX = new ArrayList<>();
        List<Double> barY = new ArrayList<>();

        int limit = Math.min(topN, sortedEntries.size());
        for (int i = 0; i < limit; i++) {
            Long topicId = sortedEntries.get(i).getKey();
            String topicName = topicNames.get(topicId);
            barX.add(topicName);

            double val = "heat".equals(metric) ? sortedEntries.get(i).getValue().getHeat() : sortedEntries.get(i).getValue().count;
            barY.add(NumberUtils.roundToTwoDecimalPlaces(val)); // 使用NumberUtils处理
        }
        result.put("topicName", specificTopicName);
        result.put("barX", barX);
        result.put("barY", barY);

        return result;
    }
    public Map<String, Object> getRadarData(Long mainTopicId, Long relatedTopicId, String startDateStr, String endDateStr) {
        // 解析时间范围
        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        // 1. 获取所有 Topic 和 时间范围内的问题
        List<Topic> allTopics = topicRepository.findAll();
        List<Question> questions = questionRepository.findAll().stream()
                .filter(q -> !q.getCreationDate().isBefore(startDateTime) && !q.getCreationDate().isAfter(endDateTime))
                .collect(Collectors.toList());

        logger.info("Radar data analysis: questions={}, topics={}, mainTopicId={}, relatedTopicId={}",
                questions.size(), allTopics.size(), mainTopicId, relatedTopicId);

        // 2. 预处理 Topic 关键词，加速匹配
        Map<Long, List<String>> topicKeywords = new HashMap<>();
        for (Topic t : allTopics) {
            List<String> kws = new ArrayList<>();
            kws.add(t.getName()); // Tag
            if (t.getRelatedKeywords() != null && !t.getRelatedKeywords().isBlank()) {
                kws.addAll(Arrays.asList(t.getRelatedKeywords().split(",")));
            }
            topicKeywords.put(t.getId(), kws.stream().map(String::trim).collect(Collectors.toList()));
        }

        // 3. 查找话题名称
        Map<Long, String> topicNames = allTopics.stream().collect(Collectors.toMap(Topic::getId, Topic::getName));
        String mainTopicName = topicNames.get(mainTopicId);
        String relatedTopicName = topicNames.get(relatedTopicId);

        // 4. 统计主话题与所有相关话题的共现情况
        Map<Long, CooccurrenceStats> mainTopicCooccurrence = new HashMap<>();

        // 4.1 遍历问题，统计主话题与所有其他话题的共现指标
        for (Question q : questions) {
            // 识别该问题包含哪些 Topic
            List<Long> matchedTopicIds = new ArrayList<>();
            String title = q.getTitle().toLowerCase();
            String tags = q.getTags() != null ? q.getTags().toLowerCase() : "";
            String body = q.getBody() != null ? q.getBody().toLowerCase() : "";

            for (Topic t : allTopics) {
                boolean isMatch = false;
                // 简单匹配逻辑：Tag 包含 OR 标题包含关键词
                for (String kw : topicKeywords.get(t.getId())) {
                    String kwLower = kw.toLowerCase();
                    if (tags.contains(kwLower) || title.contains(kwLower)) {
                        isMatch = true;
                        break;
                    }
                }
                if (isMatch) {
                    matchedTopicIds.add(t.getId());
                }
            }

            // 如果问题包含主话题，并且至少还有另一个话题
            if (matchedTopicIds.contains(mainTopicId) && matchedTopicIds.size() > 1) {
                // 统计与主话题共现的其他话题
                for (Long topicId : matchedTopicIds) {
                    if (!topicId.equals(mainTopicId)) {
                        mainTopicCooccurrence.computeIfAbsent(topicId, k -> new CooccurrenceStats()).add(q);
                    }
                }
            }
        }

        // 4.2 获取当前话题对的统计数据
        CooccurrenceStats currentStats = mainTopicCooccurrence.get(relatedTopicId);
        if (currentStats == null) {
            currentStats = new CooccurrenceStats(); // 如果没有共现数据，创建一个空的统计对象
        }

        // 5. 计算每个属性维度在所有相关话题对中的最大值和最小值
        // 问题数量的最大值和最小值
        double maxCount = mainTopicCooccurrence.values().stream()
                .mapToDouble(stats -> stats.count)
                .max().orElse(1.0);
        double minCount = mainTopicCooccurrence.values().stream()
                .mapToDouble(stats -> stats.count)
                .min().orElse(0.0);

        // 总评分的最大值和最小值
        double maxScore = mainTopicCooccurrence.values().stream()
                .mapToDouble(stats -> stats.totalScore)
                .max().orElse(1.0);
        double minScore = mainTopicCooccurrence.values().stream()
                .mapToDouble(stats -> stats.totalScore)
                .min().orElse(0.0);

        // 总浏览量的最大值和最小值
        double maxViews = mainTopicCooccurrence.values().stream()
                .mapToDouble(stats -> stats.totalViews)
                .max().orElse(1.0);
        double minViews = mainTopicCooccurrence.values().stream()
                .mapToDouble(stats -> stats.totalViews)
                .min().orElse(0.0);

        // 高质量问题数的最大值和最小值
        double maxQualityCount = mainTopicCooccurrence.values().stream()
                .mapToDouble(stats -> stats.qualityCount)
                .max().orElse(1.0);
        double minQualityCount = mainTopicCooccurrence.values().stream()
                .mapToDouble(stats -> stats.qualityCount)
                .min().orElse(0.0);

        // 总回答数的最大值和最小值
        double maxAnswerCount = mainTopicCooccurrence.values().stream()
                .mapToDouble(stats -> stats.totalAnswerCount)
                .max().orElse(1.0);
        double minAnswerCount = mainTopicCooccurrence.values().stream()
                .mapToDouble(stats -> stats.totalAnswerCount)
                .min().orElse(0.0);

        // 6. 归一化当前话题对的各个属性值
        List<Double> rawValues = Arrays.asList(
                (double) currentStats.count,
                (double) currentStats.totalScore,
                (double) currentStats.totalViews,
                (double) currentStats.qualityCount,
                (double) currentStats.totalAnswerCount
        );

        // 应用最小-最大归一化，每个属性根据自己的最大值和最小值进行归一化
        List<Double> normalizedValues = new ArrayList<>();
        normalizedValues.add(NumberUtils.roundToTwoDecimalPlaces(normalizeValue((double) currentStats.count, minCount, maxCount)));
        normalizedValues.add(NumberUtils.roundToTwoDecimalPlaces(normalizeValue((double) currentStats.totalScore, minScore, maxScore)));
        normalizedValues.add(NumberUtils.roundToTwoDecimalPlaces(normalizeValue((double) currentStats.totalViews, minViews, maxViews)));
        normalizedValues.add(NumberUtils.roundToTwoDecimalPlaces(normalizeValue((double) currentStats.qualityCount, minQualityCount, maxQualityCount)));
        normalizedValues.add(NumberUtils.roundToTwoDecimalPlaces(normalizeValue((double) currentStats.totalAnswerCount, minAnswerCount, maxAnswerCount)));
        // 7. 构建雷达图数据
        Map<String, Object> result = new HashMap<>();
        List<String> indicator = Arrays.asList("问题数量", "总评分", "总浏览量", "高质量问题数", "总回答数");

        result.put("indicator", indicator);
        result.put("values", normalizedValues); // 使用归一化后的值绘制雷达图
        result.put("rawValues", rawValues); // 保留原始数据用于显示
        result.put("mainTopic", mainTopicName);
        result.put("relatedTopic", relatedTopicName);
        result.put("timeRange", startDateStr + " 至 " + endDateStr);

        return result;
    }

    // 辅助方法：对单个值进行最小-最大归一化
    private double normalizeValue(double value, double min, double max) {
        if (max == min) {
            return 0.5;
        }
        // 应用最小-最大归一化：(x - min) / (max - min)
        return (value - min) / (max - min);
    }
}