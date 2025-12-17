package org.example.stackoverflowjavaanalysis.service;

import org.example.stackoverflowjavaanalysis.data.model.Question;
import org.example.stackoverflowjavaanalysis.data.model.Topic;
import org.example.stackoverflowjavaanalysis.data.repository.QuestionRepository;
import org.example.stackoverflowjavaanalysis.data.repository.TopicRepository;
import org.example.stackoverflowjavaanalysis.util.NumberUtils; // 导入工具类
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
public class SolvableQuestionService {

    private static final Logger logger = LoggerFactory.getLogger(SolvableQuestionService.class);

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private TopicRepository topicRepository;

    // 分类阈值配置
    private static final int MIN_ANSWERS_FOR_SOLVABLE = 3; // 高赞答案的赞数阈值 (题目要求: 赞至少3个)

    // 内部类：用于统计分类指标
    private static class CategoryStats {
        long count = 0;
        long totalScore = 0;
        long totalViews = 0;
        long totalAnswers = 0;
        long totalReputation = 0;
        long totalComments = 0;
        long totalBodyLength = 0;
        long totalTitleLength = 0;
        long totalMaxAnswerScore = 0;
        long totalTagsCount = 0;   

        void add(Question q) {
            this.count++;
            this.totalScore += (q.getScore() != null ? q.getScore() : 0);
            this.totalViews += (q.getViewCount() != null ? q.getViewCount() : 0);
            this.totalAnswers += (q.getAnswerCount() != null ? q.getAnswerCount() : 0);
            this.totalComments += (q.getQuestionCommentsNumber() != null ? q.getQuestionCommentsNumber() : 0);
            this.totalBodyLength += (q.getBody() != null ? q.getBody().length() : 0);
            this.totalTitleLength += (q.getTitle() != null ? q.getTitle().length() : 0);
            
            if (q.getOwner() != null && q.getOwner().getReputation() != null) {
                this.totalReputation += q.getOwner().getReputation();
            }

            // 计算最高赞答案分数
            int maxScore = 0;
            if (q.getAnswers() != null && !q.getAnswers().isEmpty()) {
                maxScore = q.getAnswers().stream()
                        .mapToInt(a -> a.getScore() != null ? a.getScore() : 0)
                        .max().orElse(0);
            }
            this.totalMaxAnswerScore += maxScore;

            // 计算标签数量
            if (q.getTags() != null && !q.getTags().isEmpty()) {
                // 假设格式为 <tag1><tag2> 或 tag1,tag2
                String tags = q.getTags();
                if (tags.contains("<")) {
                    // 简单统计 '<' 的数量
                    this.totalTagsCount += tags.chars().filter(ch -> ch == '<').count();
                } else {
                    // 逗号分隔
                    this.totalTagsCount += tags.split(",").length;
                }
            }
        }

        double getAvgScore() { return calcAvg(totalScore); }
        double getAvgViews() { return calcAvg(totalViews); }
        double getAvgAnswers() { return calcAvg(totalAnswers); }
        double getAvgReputation() { return calcAvg(totalReputation); }
        double getAvgComments() { return calcAvg(totalComments); }
        double getAvgBodyLength() { return calcAvg(totalBodyLength); }
        double getAvgTitleLength() { return calcAvg(totalTitleLength); }
        double getAvgMaxAnswerScore() { return calcAvg(totalMaxAnswerScore); }
        double getAvgTagsCount() { return calcAvg(totalTagsCount); }

        private double calcAvg(long total) {
            double value = count == 0 ? 0 : (double) total / count;
            return NumberUtils.roundToTwoDecimalPlaces(value);
        }
    }

    public Map<String, Object> getComparisonData(String startDateStr,
                                                 String endDateStr,
                                                 Long topicId) { // topicId 为空表示全主题
        
        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        // 1. 获取问题数据
        List<Question> questions;
        if (topicId != null) {
            Topic topic = topicRepository.findById(topicId).orElse(null);
            if (topic != null) {
                List<String> kws = new ArrayList<>();
                kws.add(topic.getName());
                if (topic.getRelatedKeywords() != null) {
                    kws.addAll(Arrays.asList(topic.getRelatedKeywords().split(",")));
                }
                // 取并集
                Set<Question> qSet = new HashSet<>();
                for (String kw : kws) {
                    qSet.addAll(questionRepository.findByTagsContainingAndCreationDateBetween(kw.trim(), startDateTime, endDateTime));
                }
                questions = new ArrayList<>(qSet);
            } else {
                questions = new ArrayList<>();
            }
        } else {
            // 全量查询
            questions = questionRepository.findAll().stream()
                    .filter(q -> !q.getCreationDate().isBefore(startDateTime) && !q.getCreationDate().isAfter(endDateTime))
                    .collect(Collectors.toList());
        }

        logger.info("Solvable analysis: total questions={}", questions.size());

        CategoryStats solvableStats = new CategoryStats();
        CategoryStats hardStats = new CategoryStats();
        
        // 时间序列数据
        Map<String, CategoryStats> solvableTimeSeries = new TreeMap<>();
        Map<String, CategoryStats> hardTimeSeries = new TreeMap<>();
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyy-MM");
        SortedSet<String> dates = new TreeSet<>();

        for (Question q : questions) {
            boolean isSolvable = isSolvable(q);
            String dateKey = q.getCreationDate().format(dateFmt);
            dates.add(dateKey);

            if (isSolvable) {
                solvableStats.add(q);
                solvableTimeSeries.computeIfAbsent(dateKey, k -> new CategoryStats()).add(q);
            } else {
                hardStats.add(q);
                hardTimeSeries.computeIfAbsent(dateKey, k -> new CategoryStats()).add(q);
            }
        }

        return buildChartData(solvableStats, hardStats, solvableTimeSeries, hardTimeSeries, dates);
    }

    private boolean isSolvable(Question q) {
        // 1. 有接受的答案
        if (Boolean.TRUE.equals(q.getHasAcceptedAnswer())) {
            return true;
        }
        
        // 2. 存在高赞答案（赞至少3个）
        if (q.getAnswers() != null) {
            for (var a : q.getAnswers()) {
                if (a.getScore() != null && a.getScore() >= 3) {
                    return true;
                }
            }
        }
        
        return false;
    }

    private Map<String, Object> buildChartData(CategoryStats solvable, CategoryStats hard,
                                               Map<String, CategoryStats> solvableSeries,
                                               Map<String, CategoryStats> hardSeries,
                                               SortedSet<String> dates) {
        Map<String, Object> result = new HashMap<>();

        // 1. 详细统计数据 (用于下方卡片展示)
        Map<String, Object> stats = new HashMap<>();
        stats.put("solvable", buildStatsMap(solvable));
        stats.put("hard", buildStatsMap(hard));
        result.put("stats", stats);

        // 2. 雷达图数据 (归一化展示)
        // 指标: 声望, 浏览量, 答案数(替换评论数), 点赞数, Body长度, Title长度
        List<Map<String, Object>> radarIndicator = new ArrayList<>();
        List<Double> solvableValues = new ArrayList<>();
        List<Double> hardValues = new ArrayList<>();

        addRadarMetric(radarIndicator, solvableValues, hardValues, "Avg Reputation", solvable.getAvgReputation(), hard.getAvgReputation());
        addRadarMetric(radarIndicator, solvableValues, hardValues, "Avg Views", solvable.getAvgViews(), hard.getAvgViews());
        addRadarMetric(radarIndicator, solvableValues, hardValues, "Avg Answers", solvable.getAvgAnswers(), hard.getAvgAnswers());
        addRadarMetric(radarIndicator, solvableValues, hardValues, "Avg Score", solvable.getAvgScore(), hard.getAvgScore());
        addRadarMetric(radarIndicator, solvableValues, hardValues, "Body Length", solvable.getAvgBodyLength(), hard.getAvgBodyLength());
        addRadarMetric(radarIndicator, solvableValues, hardValues, "Title Length", solvable.getAvgTitleLength(), hard.getAvgTitleLength());

        result.put("radarIndicator", radarIndicator);
        result.put("radarSolvable", solvableValues);
        result.put("radarHard", hardValues);

        // 3. 计算差异最大的三个指标
        List<Map<String, Object>> top3 = calculateTop3Differences(solvable, hard);
        result.put("top3Differences", top3);

        return result;
    }

    private List<Map<String, Object>> calculateTop3Differences(CategoryStats s, CategoryStats h) {
        List<Map<String, Object>> diffs = new ArrayList<>();
        
        addDiff(diffs, "Avg Reputation", s.getAvgReputation(), h.getAvgReputation());
        addDiff(diffs, "Avg Views", s.getAvgViews(), h.getAvgViews());
        addDiff(diffs, "Avg Answers", s.getAvgAnswers(), h.getAvgAnswers());
        addDiff(diffs, "Avg Comments", s.getAvgComments(), h.getAvgComments());
        addDiff(diffs, "Avg Score", s.getAvgScore(), h.getAvgScore());
        addDiff(diffs, "Body Length", s.getAvgBodyLength(), h.getAvgBodyLength());
        addDiff(diffs, "Title Length", s.getAvgTitleLength(), h.getAvgTitleLength());
        addDiff(diffs, "Max Answer Score", s.getAvgMaxAnswerScore(), h.getAvgMaxAnswerScore());
        addDiff(diffs, "Avg Tags", s.getAvgTagsCount(), h.getAvgTagsCount());

        // Sort by diff score descending
        diffs.sort((a, b) -> Double.compare((Double) b.get("diffScore"), (Double) a.get("diffScore")));

        return diffs.stream().limit(3).collect(Collectors.toList());
    }

    private void addDiff(List<Map<String, Object>> list, String name, double v1, double v2) {
        double max = Math.max(v1, v2);
        double diffScore = 0;
        if (max > 0) {
            diffScore = Math.abs(v1 - v2) / max;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("solvable", v1);
        map.put("hard", v2);
        map.put("diffScore", diffScore);
        list.add(map);
    }

    private Map<String, Object> buildStatsMap(CategoryStats s) {
        Map<String, Object> m = new HashMap<>();
        m.put("count", s.count);
        m.put("avgReputation", s.getAvgReputation());
        m.put("avgViews", s.getAvgViews());
        m.put("avgComments", s.getAvgComments());
        m.put("avgAnswers", s.getAvgAnswers());
        m.put("avgScore", s.getAvgScore());
        m.put("avgBodyLen", s.getAvgBodyLength());
        m.put("avgTitleLen", s.getAvgTitleLength());
        m.put("avgMaxAnswerScore", s.getAvgMaxAnswerScore());
        m.put("avgTagsCount", s.getAvgTagsCount());
        return m;
    }

    private void addRadarMetric(List<Map<String, Object>> indicators, List<Double> v1, List<Double> v2, String name, double val1, double val2) {
        double max = Math.max(val1, val2);
        if (max == 0) max = 1;
        max = max * 1.2; // 留出20%余量

        indicators.add(Map.of("name", name, "max", max));
        v1.add(val1);
        v2.add(val2);
    }
}