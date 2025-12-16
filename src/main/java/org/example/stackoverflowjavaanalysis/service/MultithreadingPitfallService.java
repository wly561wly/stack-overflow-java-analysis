package org.example.stackoverflowjavaanalysis.service;

import org.example.stackoverflowjavaanalysis.data.model.Answer;
import org.example.stackoverflowjavaanalysis.data.model.Question;
import org.example.stackoverflowjavaanalysis.data.repository.QuestionRepository;
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
public class MultithreadingPitfallService {

    private static final Logger logger = LoggerFactory.getLogger(MultithreadingPitfallService.class);

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private ContextAnalysis contextAnalysis;

    // 陷阱类型定义与关键词绑定
    private static final Map<String, List<String>> PITFALL_DEFINITIONS = new LinkedHashMap<>();
    static {
        PITFALL_DEFINITIONS.put("Deadlock", Arrays.asList("deadlock", "dead-lock", "hang", "stuck"));
        PITFALL_DEFINITIONS.put("Race Condition", Arrays.asList("race condition", "race-condition", "data race", "inconsistent"));
        PITFALL_DEFINITIONS.put("Thread Safety", Arrays.asList("thread safe", "thread-safe", "not safe", "unsafe", "synchroniz"));
        PITFALL_DEFINITIONS.put("Memory Visibility", Arrays.asList("visibility", "volatile", "memory model", "jmm"));
        PITFALL_DEFINITIONS.put("Performance", Arrays.asList("performance", "slow", "overhead", "context switch", "throughput"));
        PITFALL_DEFINITIONS.put("Thread Pool", Arrays.asList("thread pool", "executor", "pool size", "queue full", "rejected"));
        PITFALL_DEFINITIONS.put("Exception Handling", Arrays.asList("uncaught", "interrupted", "swallow"));
    }

    // 内部类：用于统计陷阱指标
    private static class PitfallStats {
        long count = 0;
        long totalScore = 0;
        long totalAnswers = 0;
        long solvedCount = 0; // 有接受答案的问题数
        long qualityAnswerCount = 0; // 优质回答数 (假设 score > 5)
        long withExceptionCount = 0; // 包含异常信息的问题数
        long withCodeCount = 0; // 包含代码片段的问题数
        Map<String, Long> exceptionTypeCount = new HashMap<>(); // 异常类型统计

        void add(Question q, boolean containsException, boolean containsCode, List<String> exceptions) {
            this.count++;
            this.totalScore += (q.getScore() != null ? q.getScore() : 0);
            this.totalAnswers += (q.getAnswerCount() != null ? q.getAnswerCount() : 0);

            if (q.getHasAcceptedAnswer() != null && q.getHasAcceptedAnswer()) {
                this.solvedCount++;
            }

            if (q.getScore() != null && q.getScore() > 5) {
                this.qualityAnswerCount++;
            }

            if (containsException) {
                this.withExceptionCount++;
            }

            if (containsCode) {
                this.withCodeCount++;
            }

            for (String exception : exceptions) {
                this.exceptionTypeCount.put(exception, this.exceptionTypeCount.getOrDefault(exception, 0L) + 1);
            }
        }

        double getHeat() {
            return totalScore + totalAnswers;
        }

        double getSolveRate() {
            return count == 0 ? 0 : (double) solvedCount / count;
        }

        double getExceptionRate() {
            return count == 0 ? 0 : (double) withExceptionCount / count;
        }

        double getCodeRate() {
            return count == 0 ? 0 : (double) withCodeCount / count;
        }
    }

    public Map<String, Object> getPitfallData(String startDateStr,
                                              String endDateStr,
                                              List<String> selectedPitfalls) {
        return getPitfallData(startDateStr, endDateStr, selectedPitfalls, null, "count");
    }

    public Map<String, Object> getPitfallData(String startDateStr,
                                              String endDateStr,
                                              List<String> selectedPitfalls,
                                              List<String> customWords) {
        return getPitfallData(startDateStr, endDateStr, selectedPitfalls, customWords, "count");
    }
    private Set<Question> queryQuestions(List<String> keywords, LocalDateTime start, LocalDateTime end) {
        Set<Question> result = new HashSet<>();
        for (String kw : keywords) {
            result.addAll(questionRepository.findByTagsContainingAndCreationDateBetween(kw, start, end));
            result.addAll(questionRepository.findByTitleContainingAndCreationDateBetween(kw, start, end));
            result.addAll(questionRepository.findByBodyContainingAndCreationDateBetween(kw, start, end));
        }
        return result;
    }
    public Map<String, Object> getPitfallData(String startDateStr,
                                              String endDateStr,
                                              List<String> selectedPitfalls,
                                              List<String> customWords,
                                              String lineChartAttribute) {

        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);
        List<String> keywords = Arrays.asList("thread,concurrency,deadlock,race-condition,volatile,synchronized,atomic,locks,executor,threadpool".split(","));
        List<Question> questions = new ArrayList<>();
        questions.addAll(queryQuestions(keywords, startDateTime, endDateTime));


        questions = questions.stream().distinct().collect(Collectors.toList());

        logger.info("Multithreading pitfall analysis: base questions={}, customWord={}", questions.size(),customWords
        );

        Map<String, PitfallStats> totalStats = new HashMap<>();
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
            String bodyHtml = q.getBody() != null ? q.getBody() : "";
            String bodyText = contextAnalysis.extractTextFromHtml(bodyHtml).toLowerCase();
            String fullText = title + " " + bodyText;
            String dateKey = q.getCreationDate().format(monthFmt);
            dates.add(dateKey);

            List<String> exceptions = contextAnalysis.extractExceptions(bodyHtml);
            List<String> codeSnippets = contextAnalysis.extractCodeSnippets(bodyHtml);

            // 使用NLP服务识别陷阱，但不传入自定义关键词
            Set<String> identifiedPitfalls = contextAnalysis.identifyMultithreadingPitfalls(fullText, null);

            // 结合代码片段分析
            for (String code : codeSnippets) {
                Map<String, Boolean> codeIssues = contextAnalysis.analyzeCodeForMultithreadingIssues(code);
                // 1. Thread Safety (线程安全手段)
                // 只要出现了同步、锁、原子类，就说明这段代码涉及线程安全处理
                if (codeIssues.getOrDefault("containsSynchronization", false) ||
                        codeIssues.getOrDefault("containsVolatile", false) ||
                        codeIssues.getOrDefault("containsAtomic", false) ||
                        codeIssues.getOrDefault("containsLocks", false)) {
                    identifiedPitfalls.add("Thread Safety");
                }

                // 2. Thread Pool (线程池使用)
                if (codeIssues.getOrDefault("containsThreadPools", false)) {
                    identifiedPitfalls.add("Thread Pool");
                }

                // 3. Thread Coordination (线程协作)
                // 包括 wait/notify, CountDownLatch, CyclicBarrier 等
                if (codeIssues.getOrDefault("containsWaitNotify", false)) {
                    identifiedPitfalls.add("Thread Coordination");
                }

                // 4. Deadlock Risk (死锁风险)
                // 主要是显式锁的使用，或者嵌套同步（虽然正则很难检测嵌套，但我们标记为风险）
                if (codeIssues.getOrDefault("containsDeadlockPatterns", false)) {
                    identifiedPitfalls.add("Deadlock"); // 建议改为 Risk，因为只是检测到了锁
                }

                // 5. Race Condition Risk (竞态条件风险)
                // 只有当检测到“线程上下文”+“不安全集合”时才触发
                if (codeIssues.getOrDefault("containsRaceConditionPatterns", false)) {
                    identifiedPitfalls.add("Race Condition");
                }

                // 6. Memory Visibility (内存可见性)
                // 如果代码特意使用了 volatile，通常意味着开发者在关注可见性问题
                if (codeIssues.getOrDefault("containsMemoryVisibilityPatterns", false)) {
                    identifiedPitfalls.add("Memory Visibility");
                }

                // 7. Performance Issues (性能隐患)
                // 主要是 Thread.sleep
                if (codeIssues.getOrDefault("containsPerformancePatterns", false)) {
                    identifiedPitfalls.add("Performance");
                }

                // 8. Exception Handling (异常处理)
                // 涉及 InterruptedException
                if (codeIssues.getOrDefault("containsExceptionHandlingPatterns", false)) {
                    identifiedPitfalls.add("Exception Handling");
                }
            }
            if (q.getAnswers() != null && !q.getAnswers().isEmpty()) {
                for (Answer answer : q.getAnswers()) {
                    String answerBodyText = contextAnalysis.extractTextFromHtml(answer.getBody());
                    Set<String> answerTopics = contextAnalysis.analyzeAnswerForSolutions(answerBodyText);

                    identifiedPitfalls.addAll(answerTopics);
                }
            }

            if (customWords != null && !customWords.isEmpty()) {
                for (String customEntry : customWords) {
                    try {
                        String[] parts = customEntry.split(":");
                        if (parts.length < 2) {
                            logger.warn("Invalid custom word format: {}", customEntry);
                            continue;
                        }

                        String customPitfall = parts[0].trim();
                        String words = parts[1].trim();
                        String[] word = words.split(",");

                        if (!PITFALL_DEFINITIONS.containsKey(customPitfall)) {
                            logger.warn("Unknown pitfall type: {}", customPitfall);
                            continue;
                        }
                        for (String w : word) {
                            w = w.trim();
                            if (fullText.contains(w)) {
                                identifiedPitfalls.add(customPitfall);
                                break;
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Error processing custom word: {}", customEntry, e);
                    }
                }
            }

            // 传统的关键词匹配作为补充（保留原有逻辑）
            for (Map.Entry<String, List<String>> entry : PITFALL_DEFINITIONS.entrySet()) {
                String pitfallType = entry.getKey();
                if (!totalStats.containsKey(pitfallType)) continue;

                if (identifiedPitfalls.contains(pitfallType)) continue;

                boolean isMatch = false;
                for (String kw : entry.getValue()) {
                    if (title.contains(kw)) {
                        isMatch = true;
                        break;
                    }
                }
                if (!isMatch) {
                    for (String kw : entry.getValue()) {
                        if (bodyText.contains(kw)) {
                            isMatch = true;
                            break;
                        }
                    }
                }

                if (isMatch) {
                    identifiedPitfalls.add(pitfallType);
                }
            }

            // 统计匹配的陷阱
            for (String pitfallType : identifiedPitfalls) {
                if (totalStats.containsKey(pitfallType)) {
                    boolean containsException = !exceptions.isEmpty();
                    boolean containsCode = !codeSnippets.isEmpty();

                    totalStats.get(pitfallType).add(q, containsException, containsCode, exceptions);
                    timeSeriesStats.get(pitfallType).computeIfAbsent(dateKey, k -> new PitfallStats())
                            .add(q, containsException, containsCode, exceptions);
                }
            }
        }

        return buildChartData(totalStats, timeSeriesStats, dates, lineChartAttribute);
    }

    private Map<String, Object> buildChartData(Map<String, PitfallStats> totalStats,
                                               Map<String, Map<String, PitfallStats>> timeSeriesStats,
                                               SortedSet<String> dates,
                                               String lineChartAttribute) {
        Map<String, Object> result = new HashMap<>();

        List<Map<String, Object>> pieData = new ArrayList<>();
        for (Map.Entry<String, PitfallStats> entry : totalStats.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", entry.getKey());
            item.put("value", entry.getValue().count);
            pieData.add(item);
        }
        result.put("pieData", pieData);

        List<String> barX = new ArrayList<>(totalStats.keySet());
        List<Long> barCount = new ArrayList<>();
        List<Double> barHeat = new ArrayList<>();
        List<Double> barSolveRate = new ArrayList<>();
        List<Double> barExceptionRate = new ArrayList<>();
        List<Double> barCodeRate = new ArrayList<>();

        for (String type : barX) {
            PitfallStats s = totalStats.get(type);
            barCount.add(s.count);
            barHeat.add(NumberUtils.roundToTwoDecimalPlaces(s.getHeat()));
            barSolveRate.add(NumberUtils.roundToTwoDecimalPlaces(s.getSolveRate() * 100));
            barExceptionRate.add(NumberUtils.roundToTwoDecimalPlaces(s.getExceptionRate() * 100));
            barCodeRate.add(NumberUtils.roundToTwoDecimalPlaces(s.getCodeRate() * 100));
        }
        result.put("barX", barX);
        result.put("barCount", barCount);
        result.put("barHeat", barHeat);
        result.put("barSolveRate", barSolveRate);
        result.put("barExceptionRate", barExceptionRate);
        result.put("barCodeRate", barCodeRate);

        Map<String, List<Number>> lineSeries = new HashMap<>();

        // 根据用户选择的属性生成折线图数据
        for (String type : totalStats.keySet()) {
            List<Number> values = new ArrayList<>();

            for (String d : dates) {
                PitfallStats s = timeSeriesStats.get(type).getOrDefault(d, new PitfallStats());

                switch (lineChartAttribute) {
                    case "count":
                        values.add(s.count);
                        break;
                    case "heat":
                        values.add(NumberUtils.roundToTwoDecimalPlaces(s.getHeat()));
                        break;
                    case "solveRate":
                        values.add(NumberUtils.roundToTwoDecimalPlaces(s.getSolveRate() * 100));
                        break;
                    case "exceptionRate":
                        values.add(NumberUtils.roundToTwoDecimalPlaces(s.getExceptionRate() * 100));
                        break;
                    case "codeRate":
                        values.add(NumberUtils.roundToTwoDecimalPlaces(s.getCodeRate() * 100));
                        break;
                    default:
                        values.add(s.count); // 默认显示问题数量
                }
            }

            lineSeries.put(type, values);
        }

        result.put("dates", dates);
        result.put("lineSeries", lineSeries);
        result.put("lineChartAttribute", lineChartAttribute); // 返回当前选择的属性，用于前端显示

        result.put("allPitfalls", PITFALL_DEFINITIONS.keySet());

        return result;
    }

    public Set<String> getAllPitfallTypes() {
        return PITFALL_DEFINITIONS.keySet();
    }

    // 获取可用的折线图属性选项
    public List<Map<String, String>> getLineChartAttributes() {
        List<Map<String, String>> attributes = new ArrayList<>();

        Map<String, String> countAttr = new HashMap<>();
        countAttr.put("value", "count");
        countAttr.put("label", "问题数量");
        attributes.add(countAttr);

        Map<String, String> heatAttr = new HashMap<>();
        heatAttr.put("value", "heat");
        heatAttr.put("label", "热度");
        attributes.add(heatAttr);

        Map<String, String> solveRateAttr = new HashMap<>();
        solveRateAttr.put("value", "solveRate");
        solveRateAttr.put("label", "解决率 (%)");
        attributes.add(solveRateAttr);

        Map<String, String> exceptionRateAttr = new HashMap<>();
        exceptionRateAttr.put("value", "exceptionRate");
        exceptionRateAttr.put("label", "异常率 (%)");
        attributes.add(exceptionRateAttr);

        Map<String, String> codeRateAttr = new HashMap<>();
        codeRateAttr.put("value", "codeRate");
        codeRateAttr.put("label", "代码率 (%)");
        attributes.add(codeRateAttr);

        return attributes;
    }
}