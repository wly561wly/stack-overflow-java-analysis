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

        void add(Question q) {
            this.count++;
            this.totalScore += (q.getScore() != null ? q.getScore() : 0);
            this.totalViews += (q.getViewCount() != null ? q.getViewCount() : 0);
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
                                                   String metric) { // metric: count, heat
        
        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        // 1. 获取所有 Topic 和 时间范围内的问题
        List<Topic> allTopics = topicRepository.findAll();
        // 这里为了简化，查询所有问题并在内存过滤，或者你可以扩展 Repository 按时间查所有
        // 假设 Repository 有 findAllByCreationDateBetween，如果没有，可以用 findAll 过滤
        List<Question> questions = questionRepository.findAll().stream()
                .filter(q -> !q.getCreationDate().isBefore(startDateTime) && !q.getCreationDate().isAfter(endDateTime))
                .collect(Collectors.toList());

        logger.info("分析共现: 问题数={}, Topic数={}", questions.size(), allTopics.size());

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

        // 3. 遍历问题，生成共现对
        // Map Key: "TopicIdA-TopicIdB" (IdA < IdB), Value: Stats
        Map<String, CooccurrenceStats> pairStatsMap = new HashMap<>();
        // 记录每个 Topic 出现的次数，用于热力图轴排序
        Map<Long, Integer> topicOccurrence = new HashMap<>();

        for (Question q : questions) {
            // 3.1 识别该问题包含哪些 Topic
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
                    topicOccurrence.merge(t.getId(), 1, Integer::sum);
                }
            }

            // 3.2 生成两两组合
            if (matchedTopicIds.size() < 2) continue;
            Collections.sort(matchedTopicIds); // 排序确保字典序

            for (int i = 0; i < matchedTopicIds.size(); i++) {
                for (int j = i + 1; j < matchedTopicIds.size(); j++) {
                    Long id1 = matchedTopicIds.get(i);
                    Long id2 = matchedTopicIds.get(j);
                    String key = id1 + "-" + id2;
                    
                    pairStatsMap.computeIfAbsent(key, k -> new CooccurrenceStats()).add(q);
                }
            }
        }

        // 4. 构建图表数据
        return buildChartData(pairStatsMap, allTopics, topicOccurrence, topN, metric);
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
            barY.add(val);
        }
        result.put("barX", barX);
        result.put("barY", barY);

        // --- 2. 热力图 (Matrix) ---
        // 筛选出活跃的 Topic (Top 20 活跃 Topic，避免矩阵过大)
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
                    heatmapData.add(Arrays.asList(i, j, val));
                }
            }
        }
        
        result.put("heatmapAxis", axisData);
        result.put("heatmapData", heatmapData);

        return result;
    }
}