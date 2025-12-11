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
    private static final int MIN_ANSWERS_FOR_SOLVABLE = 3;
    private static final int MIN_SCORE_FOR_SOLVABLE = 100;

    // 内部类：用于统计分类指标
    private static class CategoryStats {
        long count = 0;
        long totalScore = 0;
        long totalViews = 0;
        long totalAnswers = 0;
        long qualityCount = 0; // 优质内容 (score > 5)

        void add(Question q) {
            this.count++;
            this.totalScore += (q.getScore() != null ? q.getScore() : 0);
            this.totalViews += (q.getViewCount() != null ? q.getViewCount() : 0);
            this.totalAnswers += (q.getAnswerCount() != null ? q.getAnswerCount() : 0);
            if (q.getScore() != null && q.getScore() > 5) {
                this.qualityCount++;
            }
        }

        // 修改为返回保留两位小数的double值
        double getAvgScore() { 
            double value = count == 0 ? 0 : (double) totalScore / count;
            return NumberUtils.roundToTwoDecimalPlaces(value);
        }
        
        double getAvgViews() { 
            double value = count == 0 ? 0 : (double) totalViews / count;
            return NumberUtils.roundToTwoDecimalPlaces(value);
        }
        
        double getAvgAnswers() { 
            double value = count == 0 ? 0 : (double) totalAnswers / count;
            return NumberUtils.roundToTwoDecimalPlaces(value);
        }
        
        double getQualityRate() { 
            double value = count == 0 ? 0 : (double) qualityCount / count;
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
            // 如果选了主题，先查主题关键词，再查问题
            Topic topic = topicRepository.findById(topicId).orElse(null);
            if (topic != null) {
                List<String> kws = new ArrayList<>();
                kws.add(topic.getName());
                if (topic.getRelatedKeywords() != null) {
                    kws.addAll(Arrays.asList(topic.getRelatedKeywords().split(",")));
                }
                // 简化逻辑：取并集
                Set<Question> qSet = new HashSet<>();
                for (String kw : kws) {
                    qSet.addAll(questionRepository.findByTagsContainingAndCreationDateBetween(kw.trim(), startDateTime, endDateTime));
                }
                questions = new ArrayList<>(qSet);
            } else {
                questions = new ArrayList<>();
            }
        } else {
            // 全量查询 (实际生产中应分页或限制数量)
            questions = questionRepository.findAll().stream()
                    .filter(q -> !q.getCreationDate().isBefore(startDateTime) && !q.getCreationDate().isAfter(endDateTime))
                    .collect(Collectors.toList());
        }

        logger.info("分析可解决性: 问题总数={}", questions.size());

        // 2. 分类统计
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
        // 判定逻辑：
        // 1. 有接受答案 (假设 answerCount > 0 且 score > 0 作为简单替代，因为实体可能没 acceptedAnswerId)
        //    如果有 acceptedAnswerId 字段请替换： if (q.getAcceptedAnswerId() != null) return true;
        
        // 2. 回答数 >= 3 且 点赞 >= 100
        int answers = q.getAnswerCount() != null ? q.getAnswerCount() : 0;
        int score = q.getScore() != null ? q.getScore() : 0;
        
        if (answers >= MIN_ANSWERS_FOR_SOLVABLE && score >= MIN_SCORE_FOR_SOLVABLE) return true;

        // 简单模拟：如果 score > 5 且 answers > 0 也算比较容易解决的
        if (score > 5 && answers > 0) return true;

        return false;
    }

    private Map<String, Object> buildChartData(CategoryStats solvable, CategoryStats hard,
                                               Map<String, CategoryStats> solvableSeries,
                                               Map<String, CategoryStats> hardSeries,
                                               SortedSet<String> dates) {
        Map<String, Object> result = new HashMap<>();

        // 1. 饼图 (数量占比)
        List<Map<String, Object>> pieData = new ArrayList<>();
        pieData.add(Map.of("name", "Solvable (可解决)", "value", solvable.count));
        pieData.add(Map.of("name", "Hard-to-Solve (难解决)", "value", hard.count));
        result.put("pieData", pieData);

        // 2. 柱状图 (核心指标对比) - 这些值已经通过CategoryStats中的方法进行了格式化
        result.put("barX", Arrays.asList("平均点赞", "平均浏览", "平均回答", "优质占比(%)"));
        result.put("barSolvable", Arrays.asList(
            solvable.getAvgScore(), 
            solvable.getAvgViews(), 
            solvable.getAvgAnswers(), 
            solvable.getQualityRate() * 100
        ));
        result.put("barHard", Arrays.asList(
            hard.getAvgScore(), 
            hard.getAvgViews(), 
            hard.getAvgAnswers(), 
            hard.getQualityRate() * 100
        ));

        // 3. 雷达图 (综合维度) - 这些值也会被格式化
        List<Map<String, Object>> radarIndicator = new ArrayList<>();
        radarIndicator.add(Map.of("name", "数量 (Count)", "max", Math.max(solvable.count, hard.count) * 1.2));
        radarIndicator.add(Map.of("name", "热度 (Score)", "max", NumberUtils.roundToTwoDecimalPlaces(Math.max(solvable.getAvgScore(), hard.getAvgScore()) * 1.2)));
        radarIndicator.add(Map.of("name", "关注 (Views)", "max", NumberUtils.roundToTwoDecimalPlaces(Math.max(solvable.getAvgViews(), hard.getAvgViews()) * 1.2)));
        radarIndicator.add(Map.of("name", "互动 (Answers)", "max", NumberUtils.roundToTwoDecimalPlaces(Math.max(solvable.getAvgAnswers(), hard.getAvgAnswers()) * 1.2)));
        radarIndicator.add(Map.of("name", "质量 (Quality)", "max", 100));
        result.put("radarIndicator", radarIndicator);
        
        result.put("radarSolvable", Arrays.asList(
            solvable.count, 
            solvable.getAvgScore(), 
            solvable.getAvgViews(), 
            solvable.getAvgAnswers(), 
            solvable.getQualityRate() * 100
        ));
        result.put("radarHard", Arrays.asList(
            hard.count, 
            hard.getAvgScore(), 
            hard.getAvgViews(), 
            hard.getAvgAnswers(), 
            hard.getQualityRate() * 100
        ));

        // 4. 折线图 (趋势) - 这里是整数数据，不需要格式化
        List<Long> lineSolvable = new ArrayList<>();
        List<Long> lineHard = new ArrayList<>();
        for (String d : dates) {
            lineSolvable.add(solvableSeries.getOrDefault(d, new CategoryStats()).count);
            lineHard.add(hardSeries.getOrDefault(d, new CategoryStats()).count);
        }
        result.put("dates", dates);
        result.put("lineSolvable", lineSolvable);
        result.put("lineHard", lineHard);

        return result;
    }
}