package org.example.stackoverflowjavaanalysis.service;

import org.example.stackoverflowjavaanalysis.data.model.Question;
import org.example.stackoverflowjavaanalysis.data.repository.QuestionRepository;
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
public class MultithreadingPitfallService {

    private static final Logger logger = LoggerFactory.getLogger(MultithreadingPitfallService.class);

    @Autowired
    private QuestionRepository questionRepository;

    // 陷阱类型定义与关键词绑定
    private static final Map<String, List<String>> PITFALL_DEFINITIONS = new LinkedHashMap<>();
    static {
        PITFALL_DEFINITIONS.put("Deadlock (死锁)", Arrays.asList("deadlock", "dead-lock", "hang", "stuck"));
        PITFALL_DEFINITIONS.put("Race Condition (竞态条件)", Arrays.asList("race condition", "race-condition", "data race", "inconsistent"));
        PITFALL_DEFINITIONS.put("Thread Safety (线程安全)", Arrays.asList("thread safe", "thread-safe", "not safe", "unsafe", "synchroniz"));
        PITFALL_DEFINITIONS.put("Memory Visibility (内存可见性)", Arrays.asList("visibility", "volatile", "memory model", "jmm"));
        PITFALL_DEFINITIONS.put("Performance (性能问题)", Arrays.asList("performance", "slow", "overhead", "context switch", "throughput"));
        PITFALL_DEFINITIONS.put("Thread Pool (线程池配置)", Arrays.asList("thread pool", "executor", "pool size", "queue full", "rejected"));
        PITFALL_DEFINITIONS.put("Exception Handling (异常处理)", Arrays.asList("exception", "uncaught", "interrupted", "swallow"));
    }

    // 内部类：用于统计陷阱指标
    private static class PitfallStats {
        long count = 0;
        long totalScore = 0;
        long totalAnswers = 0;
        long solvedCount = 0; // 有接受答案的问题数
        long qualityAnswerCount = 0; // 优质回答数 (假设 score > 5)

        void add(Question q) {
            this.count++;
            this.totalScore += (q.getScore() != null ? q.getScore() : 0);
            this.totalAnswers += (q.getAnswerCount() != null ? q.getAnswerCount() : 0);
            // 假设 Question 实体没有 isAnswered 字段，这里用 answerCount > 0 模拟，或者你需要扩展实体
            // 如果实体有 acceptedAnswerId，可以用 acceptedAnswerId != null
            if (q.getAnswerCount() != null && q.getAnswerCount() > 0) {
                this.solvedCount++;
            }
            // 简单模拟优质回答判定
            if (q.getScore() != null && q.getScore() > 5) {
                this.qualityAnswerCount++;
            }
        }

        double getHeat() {
            return totalScore + totalAnswers;
        }

        double getSolveRate() {
            return count == 0 ? 0 : (double) solvedCount / count;
        }
    }

    public Map<String, Object> getPitfallData(String startDateStr,
                                              String endDateStr,
                                              List<String> selectedPitfalls) {
        
        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        // 1. 获取 "multithreading" 相关的所有问题
        // 这里假设 Tag 包含 "multithreading" 或 "thread" 或 "concurrency"
        // 为了简化，我们复用之前的模糊查询，或者你可以定义更精确的查询
        List<Question> questions = new ArrayList<>();
        questions.addAll(questionRepository.findByTagsContainingAndCreationDateBetween("multithreading", startDateTime, endDateTime));
        questions.addAll(questionRepository.findByTagsContainingAndCreationDateBetween("concurrency", startDateTime, endDateTime));
        questions.addAll(questionRepository.findByTagsContainingAndCreationDateBetween("thread", startDateTime, endDateTime));
        
        // 去重
        questions = questions.stream().distinct().collect(Collectors.toList());

        logger.info("分析多线程陷阱: 基础问题数={}", questions.size());

        // 2. 识别陷阱并统计
        // Map<PitfallType, Stats>
        Map<String, PitfallStats> totalStats = new HashMap<>();
        // Map<PitfallType, Map<DateStr, Stats>>
        Map<String, Map<String, PitfallStats>> timeSeriesStats = new HashMap<>();
        
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("yyyy-MM");
        SortedSet<String> dates = new TreeSet<>();

        for (String pitfall : PITFALL_DEFINITIONS.keySet()) {
            if (selectedPitfalls != null && !selectedPitfalls.isEmpty() && !selectedPitfalls.contains(pitfall)) {
                continue;
            }
            totalStats.put(pitfall, new PitfallStats());
            timeSeriesStats.put(pitfall, new HashMap<>());
        }

        for (Question q : questions) {
            String title = q.getTitle().toLowerCase();
            String body = q.getBody() != null ? q.getBody().toLowerCase() : "";
            String dateKey = q.getCreationDate().format(monthFmt);
            dates.add(dateKey);

            for (Map.Entry<String, List<String>> entry : PITFALL_DEFINITIONS.entrySet()) {
                String pitfallType = entry.getKey();
                // 如果用户没选这个类型，跳过
                if (!totalStats.containsKey(pitfallType)) continue;

                boolean isMatch = false;
                // 优先匹配标题
                for (String kw : entry.getValue()) {
                    if (title.contains(kw)) {
                        isMatch = true;
                        break;
                    }
                }
                // 标题未匹配，匹配全文
                if (!isMatch) {
                    for (String kw : entry.getValue()) {
                        if (body.contains(kw)) {
                            isMatch = true;
                            break;
                        }
                    }
                }

                if (isMatch) {
                    totalStats.get(pitfallType).add(q);
                    timeSeriesStats.get(pitfallType).computeIfAbsent(dateKey, k -> new PitfallStats()).add(q);
                }
            }
        }

        return buildChartData(totalStats, timeSeriesStats, dates);
    }

    private Map<String, Object> buildChartData(Map<String, PitfallStats> totalStats,
                                               Map<String, Map<String, PitfallStats>> timeSeriesStats,
                                               SortedSet<String> dates) {
        Map<String, Object> result = new HashMap<>();

        // 1. 饼图数据 (问题数量占比)
        List<Map<String, Object>> pieData = new ArrayList<>();
        for (Map.Entry<String, PitfallStats> entry : totalStats.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", entry.getKey());
            item.put("value", entry.getValue().count);
            pieData.add(item);
        }
        result.put("pieData", pieData);

        // 2. 柱状图数据 (多指标对比)
        List<String> barX = new ArrayList<>(totalStats.keySet());
        List<Long> barCount = new ArrayList<>();
        List<Double> barHeat = new ArrayList<>();
        List<Double> barSolveRate = new ArrayList<>();

        for (String type : barX) {
            PitfallStats s = totalStats.get(type);
            barCount.add(s.count);
            barHeat.add(s.getHeat());
            barSolveRate.add(s.getSolveRate() * 100); // 百分比
        }
        result.put("barX", barX);
        result.put("barCount", barCount);
        result.put("barHeat", barHeat);
        result.put("barSolveRate", barSolveRate);

        // 3. 折线图数据 (趋势)
        Map<String, List<Number>> lineSeries = new HashMap<>();
        // 这里我们只生成 Count 和 Heat 的趋势，避免数据量过大
        for (String type : totalStats.keySet()) {
            List<Number> counts = new ArrayList<>();
            List<Number> heats = new ArrayList<>();
            
            for (String d : dates) {
                PitfallStats s = timeSeriesStats.get(type).getOrDefault(d, new PitfallStats());
                counts.add(s.count);
                heats.add(s.getHeat());
            }
            lineSeries.put(type + "_count", counts);
            lineSeries.put(type + "_heat", heats);
        }
        
        result.put("dates", dates);
        result.put("lineSeries", lineSeries);
        
        // 返回所有定义的陷阱类型供前端筛选
        result.put("allPitfalls", PITFALL_DEFINITIONS.keySet());

        return result;
    }
    
    public Set<String> getAllPitfallTypes() {
        return PITFALL_DEFINITIONS.keySet();
    }
}