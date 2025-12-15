package org.example.stackoverflowjavaanalysis.service;

import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NLPService {
    private static final Logger logger = LoggerFactory.getLogger(NLPService.class);

    // 正则表达式模式
    private static final Pattern EXCEPTION_PATTERN = Pattern.compile("\\b([a-zA-Z]+Exception|Error):[^\\n]*");

    private static final Pattern CODE_PATTERN = Pattern.compile("<code>([\\s\\S]*?)</code>");
    private static final Pattern THREAD_SAFE_PATTERN = Pattern.compile("(synchroniz|volatile|thread.*?safe|atomic|lock)");
    private static final Pattern DEADLOCK_PATTERN = Pattern.compile("(deadlock|dead-lock|hang|stuck|circular.*?wait)");
    private static final Pattern RACE_CONDITION_PATTERN = Pattern.compile("(race.*?condition|data.*?race|inconsistent|unexpected.*?result)");
    private static final Pattern MEMORY_VISIBILITY_PATTERN = Pattern.compile("(visibility|memory.*?model|jmm|not.*?visible)");
    private static final Pattern THREAD_POOL_PATTERN = Pattern.compile("(thread.*?pool|executor|pool.*?size|queue.*?full|rejected.*?execution)");
    private static final Pattern PERFORMANCE_PATTERN = Pattern.compile("(slow|performance|overhead|context.*?switch|throughput|latency)");
    private static final Pattern EXCEPTION_HANDLING_PATTERN = Pattern.compile("(exception|uncaught|interrupted|swallow|ignore.*?exception)");

    public NLPService() {
        logger.info("NLPService initialized with basic text analysis capabilities");
    }

    /**
     * 从HTML内容中提取纯文本
     */
    public String extractTextFromHtml(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        return Jsoup.parse(html).text();
    }

    /**
     * 从文本中提取异常信息
     */
    public List<String> extractExceptions(String text) {
        List<String> exceptions = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return exceptions;
        }

        Matcher matcher = EXCEPTION_PATTERN.matcher(text);
        while (matcher.find()) {
            exceptions.add(matcher.group(1));
        }
        return exceptions;
    }

    /**
     * 从HTML内容中提取代码片段
     */
    public List<String> extractCodeSnippets(String html) {
        List<String> codeSnippets = new ArrayList<>();
        if (html == null || html.isEmpty()) {
            return codeSnippets;
        }

        Matcher matcher = CODE_PATTERN.matcher(html);
        while (matcher.find()) {
            codeSnippets.add(matcher.group(1));
        }
        return codeSnippets;
    }

    /**
     * 分析代码片段中的多线程问题
     */
    public Map<String, Boolean> analyzeCodeForMultithreadingIssues(String code) {
        Map<String, Boolean> issues = new HashMap<>();
        if (code == null || code.isEmpty()) {
            return issues;
        }

        String lowerCode = code.toLowerCase();

        issues.put("containsSynchronization", lowerCode.contains("synchroniz"));
        issues.put("containsVolatile", lowerCode.contains("volatile"));
        issues.put("containsAtomic", lowerCode.contains("atomic"));
        issues.put("containsLocks", lowerCode.contains("lock") || lowerCode.contains("readwritelock"));
        issues.put("containsThreadPools", lowerCode.contains("executors") || lowerCode.contains("threadpool") || lowerCode.contains("executorservice"));
        issues.put("containsWaitNotify", lowerCode.contains("wait()") || lowerCode.contains("notify()") || lowerCode.contains("notifyall()"));

        return issues;
    }

    /**
     * 识别文本中的多线程陷阱类型
     */
    public Set<String> identifyMultithreadingPitfalls(String text) {
        return identifyMultithreadingPitfalls(text, null);
    }

    /**
     * 识别文本中的多线程陷阱类型，支持自定义匹配词
     */
    public Set<String> identifyMultithreadingPitfalls(String text, List<String> customWords) {
        Set<String> pitfalls = new HashSet<>();
        if (text == null || text.isEmpty()) {
            return pitfalls;
        }

        String lowerText = text.toLowerCase();

        if (DEADLOCK_PATTERN.matcher(lowerText).find()) {
            pitfalls.add("Deadlock");
        }
        if (RACE_CONDITION_PATTERN.matcher(lowerText).find()) {
            pitfalls.add("Race Condition");
        }
        if (THREAD_SAFE_PATTERN.matcher(lowerText).find()) {
            pitfalls.add("Thread Safety");
        }
        if (MEMORY_VISIBILITY_PATTERN.matcher(lowerText).find()) {
            pitfalls.add("Memory Visibility");
        }
        if (THREAD_POOL_PATTERN.matcher(lowerText).find()) {
            pitfalls.add("Thread Pool");
        }
        if (PERFORMANCE_PATTERN.matcher(lowerText).find()) {
            pitfalls.add("Performance");
        }
        if (EXCEPTION_HANDLING_PATTERN.matcher(lowerText).find()) {
            pitfalls.add("Exception Handling");
        }

        return pitfalls;
    }

    /**
     * 分析文本的情感倾向（简单实现）
     */
    public int analyzeSentiment(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        String lowerText = text.toLowerCase();
        int sentiment = 0;

        List<String> positiveWords = Arrays.asList("solved", "fixed", "working", "correct", "good", "great", "excellent", "helpful");
        List<String> negativeWords = Arrays.asList("problem", "error", "issue", "wrong", "bad", "failed", "doesn't work", "broken");

        for (String word : positiveWords) {
            if (lowerText.contains(word)) {
                sentiment++;
            }
        }

        for (String word : negativeWords) {
            if (lowerText.contains(word)) {
                sentiment--;
            }
        }

        return sentiment;
    }

    /**
     * 提取文本中的关键短语（简单实现）
     */
    public List<String> extractKeyPhrases(String text, int maxPhrases) {
        List<String> phrases = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return phrases;
        }

        Pattern pattern = Pattern.compile("(\\b[a-zA-Z]+(?:\\s+[a-zA-Z]+){1,3}\\b)");
        Matcher matcher = pattern.matcher(text);

        while (matcher.find() && phrases.size() < maxPhrases) {
            String phrase = matcher.group(1);
            if (phrase.length() > 5) {
                phrases.add(phrase);
            }
        }

        return phrases;
    }
}