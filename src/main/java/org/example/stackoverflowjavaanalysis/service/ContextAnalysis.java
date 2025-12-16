package org.example.stackoverflowjavaanalysis.service;

import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ContextAnalysis {
    private static final Logger logger = LoggerFactory.getLogger(ContextAnalysis.class);

    // 正则表达式模式
    private static final Pattern EXCEPTION_PATTERN = Pattern.compile("\\b([a-zA-Z]+Exception|Error):[^\\n]*");

    private static final Pattern CODE_PATTERN = Pattern.compile("<code>([\\s\\S]*?)</code>");
    private static final Pattern THREAD_SAFE_PATTERN = Pattern.compile(
            "\\b(thread[\\s-]?safe(ty)?|synchroniz(ed|ation)|atomic(ity)?|concurrent[\\s-]?modification|not[\\s-]?thread[\\s-]?safe|unsafe)\\b",
            Pattern.CASE_INSENSITIVE);

    // ==========================================
    // 2. Deadlock (死锁)
    // 匹配：deadlock, hang, stuck, circular wait, starvation
    // ==========================================
    private static final Pattern DEADLOCK_PATTERN = Pattern.compile(
            "\\b(dead[\\s-]?lock|hang(s|ing|ed)?|stuck|circular[\\s-]?wait|starvation|live[\\s-]?lock)\\b",
            Pattern.CASE_INSENSITIVE);

    // ==========================================
    // 3. Race Condition (竞态条件)
    // 匹配：race condition, data race, inconsistent state, check-then-act
    // ==========================================
    private static final Pattern RACE_CONDITION_PATTERN = Pattern.compile(
            "\\b(race[\\s-]?condition|data[\\s-]?race|inconsistent[\\s-]?state|check[\\s-]?then[\\s-]?act|atomicity[\\s-]?failure)\\b",
            Pattern.CASE_INSENSITIVE);

    // ==========================================
    // 4. Memory Visibility (内存可见性)
    // 匹配：visibility, volatile, memory model, happens-before, stale data
    // ==========================================
    private static final Pattern MEMORY_VISIBILITY_PATTERN = Pattern.compile(
            "\\b(visibility|memory[\\s-]?model|jmm|happens[\\s-]?before|volatile|stale[\\s-]?data|barrier|fence)\\b",
            Pattern.CASE_INSENSITIVE);

    // ==========================================
    // 5. Thread Pool (线程池)
    // 匹配：thread pool, executor, queue full, rejected execution
    // ==========================================
    private static final Pattern THREAD_POOL_PATTERN = Pattern.compile(
            "\\b(thread[\\s-]?pool|executor|worker[\\s-]?thread|pool[\\s-]?size|queue[\\s-]?full|rejected[\\s-]?execution)\\b",
            Pattern.CASE_INSENSITIVE);

    // ==========================================
    // 6. Performance (性能问题)
    // 匹配：latency, throughput, context switch, overhead, busy wait, bottleneck
    // ==========================================
    private static final Pattern PERFORMANCE_PATTERN = Pattern.compile(
            "\\b(latency|throughput|context[\\s-]?switch|overhead|bottle[\\s-]?neck|busy[\\s-]?wait|spin[\\s-]?lock|cpu[\\s-]?bound)\\b",
            Pattern.CASE_INSENSITIVE);

    // ==========================================
    // 7. Exception Handling (异常处理)
    // 匹配：interrupted, swallow exception, uncaught, ignore exception
    // ==========================================
    private static final Pattern EXCEPTION_HANDLING_PATTERN = Pattern.compile(
            "\\b(interrupted|uncaught|swallow(ed|ing)?[\\s-]?exception|ignore(d|ing)?[\\s-]?exception|future[\\.]?get)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern THREAD_SAFE_ANSWER = Pattern.compile("\\b(thread[\\s-]?safe(ty)?|synchroniz(ed|ation)|concurrent(hash)?map|copyonwrite|blockingqueue|atomic\\w*|longadder|cas|reentrantlock|stampedlock|readwritelock|immutable|final|threadlocal)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEADLOCK_ANSWER = Pattern.compile("\\b(dead[\\s-]?lock|circular[\\s-]?wait|lock[\\s-]?order(ing)?|try[\\s-]?lock|time(out|d)|avoid[\\s-]?nested[\\s-]?lock)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern RACE_CONDITION_ANSWER = Pattern.compile("\\b(race[\\s-]?condition|data[\\s-]?race|atomic(ity|ally)?|compound[\\s-]?action|critical[\\s-]?section|mutex|isolation|transaction(al)?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEMORY_VISIBILITY_ANSWER = Pattern.compile("\\b(visibility|memory[\\s-]?model|jmm|happens[\\s-]?before|volatile|barrier|fence|safe[\\s-]?publication)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern THREAD_POOL_ANSWER = Pattern.compile("\\b(thread[\\s-]?pool|executor(service)?|core[\\s-]?pool[\\s-]?size|blocking[\\s-]?queue|rejected[\\s-]?execution)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PERFORMANCE_ANSWER = Pattern.compile("\\b(performance|throughput|latency|context[\\s-]?switch|busy[\\s-]?wait|lock[\\s-]?free|wait[\\s-]?free)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXCEPTION_HANDLING_ANSWER = Pattern.compile("\\b(exception|interrupted|restore[\\s-]?interrupt|uncaught[\\s-]?exception|completablefuture)\\b", Pattern.CASE_INSENSITIVE);

    /**
     * 新增方法：分析回答文本，返回对应的陷阱/领域类型
     */
    public Set<String> analyzeAnswerForSolutions(String answerText) {
        Set<String> topics = new HashSet<>();
        if (answerText == null || answerText.isEmpty()) return topics;

        // 注意：这里的字符串必须与 MultithreadingPitfallService 中的 PITFALL_DEFINITIONS 的 Key 保持一致
        if (DEADLOCK_ANSWER.matcher(answerText).find()) topics.add("Deadlock");
        if (RACE_CONDITION_ANSWER.matcher(answerText).find()) topics.add("Race Condition");
        if (THREAD_SAFE_ANSWER.matcher(answerText).find()) topics.add("Thread Safety");
        if (MEMORY_VISIBILITY_ANSWER.matcher(answerText).find()) topics.add("Memory Visibility");
        if (THREAD_POOL_ANSWER.matcher(answerText).find()) topics.add("Thread Pool");
        if (PERFORMANCE_ANSWER.matcher(answerText).find()) topics.add("Performance");
        if (EXCEPTION_HANDLING_ANSWER.matcher(answerText).find()) topics.add("Exception Handling");

        return topics;
    }
    public ContextAnalysis() {
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

    public Map<String, Boolean> analyzeCodeForMultithreadingIssues(String code) {
        Map<String, Boolean> issues = new HashMap<>();
        if (code == null || code.isEmpty()) {
            return issues;
        }

        // 移除注释（简单版，防止注释干扰检测）
        // 注意：这只是一个简单的正则，处理复杂嵌套注释可能不完美，但在简单场景够用
        String cleanCode = code.replaceAll("//.*|/\\*[\\s\\S]*?\\*/", " ");

        // 1. 基础同步机制（检测是否使用了多线程工具）
        issues.put("usesSynchronization", check(cleanCode, "synchronized"));
        issues.put("usesVolatile", check(cleanCode, "volatile"));
        issues.put("usesAtomic", check(cleanCode, "AtomicInteger", "AtomicLong", "AtomicReference", "LongAdder"));
        issues.put("usesExplicitLocks", check(cleanCode, "ReentrantLock", "ReentrantReadWriteLock", "StampedLock", "\\.lock\\(", "\\.tryLock\\("));
        issues.put("usesThreadPools", check(cleanCode, "ExecutorService", "ThreadPoolExecutor", "Executors\\.new", "ForkJoinPool", "CompletableFuture"));

        // 2. 线程协作模式
        issues.put("usesWaitNotify", check(cleanCode, "\\.wait\\(", "\\.notify\\(", "\\.notifyAll\\("));
        issues.put("usesCondition", check(cleanCode, "Condition", "\\.await\\(", "\\.signal\\("));
        issues.put("usesBarriers", check(cleanCode, "CountDownLatch", "CyclicBarrier", "Semaphore", "Phaser"));

        // 3. 潜在风险模式 (Risk Detection)
        issues.put("riskManualLocking", check(cleanCode, "Lock", "\\.lock\\("));

        // 风险：使用了非线程安全的集合 (在多线程上下文中这很危险，但单看代码很难确定上下文)
        // 这里的逻辑是：如果代码里出现了线程池或锁，同时又出现了 HashMap，则标记为警告
        boolean hasThreadingContext = issues.get("usesThreadPools") || issues.get("usesExplicitLocks") || issues.get("usesSynchronization");
        issues.put("riskUnsafeCollectionsInThreadContext",
                hasThreadingContext && check(cleanCode, "HashMap", "ArrayList", "SimpleDateFormat", "StringBuilder"));

        // 风险：Thread.sleep 通常是不推荐的生产代码写法（应用 CountDownLatch 等替代）
        issues.put("riskThreadSleep", check(cleanCode, "Thread\\.sleep", "TimeUnit\\.[A-Z]+\\.sleep"));

        // 风险：捕获了 InterruptedException 但可能吞掉了异常 (忽略不处理)
        // 正则逻辑：catch 括号里有 InterruptedException
        issues.put("catchesInterruptedException", check(cleanCode, "catch\\s*\\(.*InterruptedException"));

        return issues;
    }

    /**
     * 使用正则进行全字匹配，避免 "block" 匹配到 "lock"
     */
    private boolean check(String code, String... patterns) {
        for (String pattern : patterns) {
            // \b 表示单词边界，防止部分匹配
            // Pattern.CASE_INSENSITIVE 让匹配不区分大小写
            // 如果 pattern 本身包含符号（如 . 或 ( ），则假设调用者已经处理好转义，或者我们不使用 \b 包裹非单词字符
            String regex;
            if (pattern.contains("\\")) {
                regex = pattern; // 调用者负责正则写法
            } else {
                regex = "\\b" + pattern + "\\b"; // 自动加边界
            }

            if (Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(code).find()) {
                return true;
            }
        }
        return false;
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
}