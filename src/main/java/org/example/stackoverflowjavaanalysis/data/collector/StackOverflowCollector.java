// filepath: d:\Desktop\stack-overflow-java-analysis\src\main\java\org\example\stackoverflowjavaanalysis\data\collector\StackOverflowCollector.java
package org.example.stackoverflowjavaanalysis.data.collector;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.stackoverflowjavaanalysis.data.model.*;
import org.example.stackoverflowjavaanalysis.data.model.ApiQuestion.Owner; // 导入 ApiQuestion.Owner
import org.example.stackoverflowjavaanalysis.data.repository.AnswerRepository;
import org.example.stackoverflowjavaanalysis.data.repository.CommentRepository;
import org.example.stackoverflowjavaanalysis.data.repository.QuestionRepository;
import org.example.stackoverflowjavaanalysis.data.repository.SOUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder; // 必须添加此 import

import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class StackOverflowCollector {

    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final CommentRepository commentRepository;
    private final SOUserRepository soUserRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Value("${stackoverflow.api.base-url}")
    private String baseUrl;

    @Value("${stackoverflow.api.key}")
    private String apiKey;

    @Value("${stackoverflow.api.tags}")
    private String tags;

    @Value("${stackoverflow.api.page-size}")
    private int pageSize;

    private final int API_PAGE_SIZE = 100;
    
    // 修改点 1：使用官方标准 filter "withbody"，它包含问题、答案和评论的 body，且不含特殊字符
    private final String FILTER_WITH_BODY = "withbody"; 

    public StackOverflowCollector(QuestionRepository questionRepository,
                                  AnswerRepository answerRepository,
                                  CommentRepository commentRepository,
                                  SOUserRepository soUserRepository) {
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.commentRepository = commentRepository;
        this.soUserRepository = soUserRepository;
        this.restTemplate = new RestTemplate();
    }

    /**
     * 入口 1：抓取 Top N 热门问题及其详情
     * 直接调用通用的 fetchQuestionsByParams
     */
    public void collectTopQuestionsWithDetails(int limit) {
        System.out.println("Starting collection of " + limit + " top questions...");
        // 复用通用逻辑：按 votes 排序，使用默认 tags
        fetchQuestionsByParams(limit, "votes", this.tags);
    }

    /**
     * 入口 2：完整数据采集计划
     */
    public void collectFullPlan(boolean dryRun) {
        int popular = 3000;   
        int random = 1500;    
        int scenario = 500;   
        System.out.println("collectFullPlan: plan -> popular=" + popular + ", random=" + random + ", scenario=" + scenario + ", dryRun=" + dryRun);
        
        if (dryRun) {
            System.out.println("collectFullPlan: dry run - no network requests will be performed");
            return;
        }

        // 1) 热门
        System.out.println("collectFullPlan: fetching popular questions...");
        fetchQuestionsByParams(popular, "votes", "java");
        sleep(10_000L);

        // 2) 随机
        System.out.println("collectFullPlan: fetching random questions...");
        fetchQuestionsByParams(random, "random", "java");
        sleep(10_000L);

        // 3) 场景化
        System.out.println("collectFullPlan: fetching scenario questions...");
        fetchQuestionsByParams(scenario, "activity", "java;multithreading");
        System.out.println("collectFullPlan: finished full plan");
    }

    /**
     * 入口 3：小规模样本采集计划 (测试用)
     */
    public void collectSamplePlan() {
        int perMode = 50; 
        System.out.println("collectSamplePlan: start sample run, perMode=" + perMode);
        fetchQuestionsByParams(perMode, "votes", "java");
        fetchQuestionsByParams(perMode, "random", "java");
        fetchQuestionsByParams(perMode, "activity", "java;multithreading");
        System.out.println("collectSamplePlan: finished sample run");
    }

    /**
     * 核心方法：根据参数抓取 Question，并触发 Answer/Comment 的抓取
     * 修改点 2：使用 UriComponentsBuilder 替代 String.format，确保参数正确编码
     */
    public void fetchQuestionsByParams(int limit, String sort, String tagged) {
        try {
            int page = 1;
            int pageSizeLocal = 100; // API max per page
            List<Long> collectedIds = new ArrayList<>();
            boolean hasMore = true;
            String tagParam = (tagged == null || tagged.isBlank()) ? this.tags : tagged;

            // 新增：设定时间戳 2020-01-01 00:00:00 UTC = 1577836800
            long fromDate = 1577836800L;

            System.out.println(String.format("Fetching questions: limit=%d, sort=%s, tags=%s, fromDate=%d (2020+)", limit, sort, tagParam, fromDate));

            while (collectedIds.size() < limit && hasMore) {
                int requestPage = page;
                String sortParam = sort;
                if ("random".equalsIgnoreCase(sort)) {
                    sortParam = "votes";
                    requestPage = new Random().nextInt(100) + 1; 
                }

                // 使用 UriComponentsBuilder 构建 URL
                URI questionUri = UriComponentsBuilder.fromUriString(baseUrl + "/questions")
                        .queryParam("page", requestPage)
                        .queryParam("pagesize", pageSizeLocal)
                        .queryParam("fromdate", fromDate) // 关键修改：添加时间过滤参数
                        .queryParam("order", "desc")
                        .queryParam("sort", sortParam)
                        .queryParam("tagged", tagParam)
                        .queryParam("site", "stackoverflow")
                        .queryParam("key", apiKey)
                        .queryParam("filter", FILTER_WITH_BODY)
                        .build()
                        .toUri();

                String json = restTemplate.getForObject(questionUri, String.class);
                if (json == null) break;

                JsonNode root = mapper.readTree(json);
                JsonNode items = root.path("items");
                if (items.isEmpty()) {
                    System.out.println("No items returned for page " + requestPage);
                    break;
                }

                for (JsonNode node : items) {
                    if (collectedIds.size() == 0) {
                        System.out.println("DEBUG: First question JSON: " + node.toString());
                    }
                    if (collectedIds.size() >= limit) break;
                    ApiQuestion aq = mapper.treeToValue(node, ApiQuestion.class);
                    if (aq == null || aq.getQuestionId() == null) continue;
                    saveQuestionFromApi(aq);
                    collectedIds.add(aq.getQuestionId());
                }

                hasMore = root.path("has_more").asBoolean(false);
                int backoff = root.path("backoff").asInt(0);
                if (backoff > 0) sleep(backoff * 1000L);
                
                page++;
                sleep(200L);
            }

            System.out.println("Questions collected: " + collectedIds.size() + ". Now fetching details...");

            if (!collectedIds.isEmpty()) {
                // 抓取详情：每个问题取 Top 3 Answers，每个 Post 取 Top 5 Comments
                fetchTopAnswersAndCommentsForQuestions(collectedIds, 3, 5);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 核心方法：抓取 Answer 和 Comment
     * 修复：先获取 String 再解析为 JsonNode，避免 RestTemplate 直接反序列化抽象类 JsonNode 报错
     */
    private void fetchTopAnswersAndCommentsForQuestions(List<Long> questionIds, int topAnswersPerQuestion, int topCommentsPerPost) {
        if (questionIds == null || questionIds.isEmpty()) return;
        
        int chunk = 20; // 保持小批次
        for (int i = 0; i < questionIds.size(); i += chunk) {
            List<Long> sub = questionIds.subList(i, Math.min(questionIds.size(), i + chunk));
            String idsParam = sub.stream().map(String::valueOf).collect(Collectors.joining(";"));

            try {
                // ================= 1. 抓取 Answers =================
                List<ApiAnswer> candidateAnswers = new ArrayList<>();
                int aPage = 1;
                boolean aHasMore = true;
                
                while (aHasMore && aPage <= 5) { 
                    try {
                        URI answersUri = UriComponentsBuilder.fromUriString(String.format("%s/questions/%s/answers", baseUrl, idsParam))
                                .queryParam("page", aPage)
                                .queryParam("pagesize", 100)
                                .queryParam("order", "desc")
                                .queryParam("sort", "votes")
                                .queryParam("site", "stackoverflow")
                                .queryParam("key", apiKey)
                                .queryParam("filter", FILTER_WITH_BODY)
                                .build()
                                .toUri();

                        // 修复点：获取 String 而不是 JsonNode.class
                        String json = restTemplate.getForObject(answersUri, String.class);
                        if (json == null) break;

                        JsonNode root = mapper.readTree(json);
                        JsonNode items = root.path("items");
                        for (JsonNode node : items) {
                            candidateAnswers.add(mapper.treeToValue(node, ApiAnswer.class));
                        }
                        
                        aHasMore = root.path("has_more").asBoolean(false);
                        int backoff = root.path("backoff").asInt(0);
                        if (backoff > 0) sleep(backoff * 1000L);
                        aPage++;
                    } catch (Exception e) {
                        System.err.println("Error fetching answers (Page " + aPage + "): " + e.getMessage());
                        break; 
                    }
                }

                Map<Long, List<ApiAnswer>> answersByQ = candidateAnswers.stream()
                        .filter(a -> a.getQuestionId() != null)
                        .collect(Collectors.groupingBy(ApiAnswer::getQuestionId));

                List<Long> savedAnswerIds = new ArrayList<>();

                for (Long qId : sub) {
                    List<ApiAnswer> list = answersByQ.getOrDefault(qId, Collections.emptyList());
                    list.sort((a1, a2) -> Integer.compare(a2.getScore(), a1.getScore()));
                    
                    int limit = Math.min(topAnswersPerQuestion, list.size());
                    for (int k = 0; k < limit; k++) {
                        ApiAnswer aa = list.get(k);
                        if (answerRepository.existsBySoId(aa.getAnswerId())) {
                            savedAnswerIds.add(aa.getAnswerId());
                            continue;
                        }
                        
                        Answer ans = new Answer();
                        ans.setSoId(aa.getAnswerId());
                        ans.setBody(aa.getBody());
                        ans.setScore(aa.getScore());
                        ans.setIsAccepted(aa.getIsAccepted());
                        ans.setContentLicense(aa.getContentLicense());
                        if (aa.getLastEditDate() != null) {
                            ans.setLastEditDate(LocalDateTime.ofInstant(Instant.ofEpochSecond(aa.getLastEditDate()), ZoneId.systemDefault()));
                        }
                        
                        questionRepository.findBySoId(qId).ifPresent(ans::setQuestion);
                        
                        if (aa.getOwner() != null && aa.getOwner().getUserId() != null) {
                            ans.setOwner(saveOrUpdateUser(aa.getOwner()));
                        }
                        
                        answerRepository.save(ans);
                        savedAnswerIds.add(ans.getSoId());
                    }
                }

                // ================= 2. 抓取 Question Comments =================
                List<ApiComment> candidateQComments = new ArrayList<>();
                int cPage = 1;
                boolean cHasMore = true;
                while (cHasMore && cPage <= 3) { 
                    try {
                        URI commentsUri = UriComponentsBuilder.fromUriString(String.format("%s/questions/%s/comments", baseUrl, idsParam))
                                .queryParam("page", cPage)
                                .queryParam("pagesize", 100)
                                .queryParam("order", "desc")
                                .queryParam("sort", "votes")
                                .queryParam("site", "stackoverflow")
                                .queryParam("key", apiKey)
                                .queryParam("filter", FILTER_WITH_BODY)
                                .build()
                                .toUri();

                        // 修复点：获取 String
                        String json = restTemplate.getForObject(commentsUri, String.class);
                        if (json == null) break;

                        JsonNode root = mapper.readTree(json);
                        for (JsonNode node : root.path("items")) {
                            candidateQComments.add(mapper.treeToValue(node, ApiComment.class));
                        }
                        cHasMore = root.path("has_more").asBoolean(false);
                        int backoff = root.path("backoff").asInt(0);
                        if (backoff > 0) sleep(backoff * 1000L);
                        cPage++;
                    } catch (Exception e) {
                        System.err.println("Error fetching Q-comments: " + e.getMessage());
                        break;
                    }
                }

                Map<Long, List<ApiComment>> qCommentsByPost = candidateQComments.stream()
                        .filter(c -> c.getPostId() != null)
                        .collect(Collectors.groupingBy(ApiComment::getPostId));

                for (Long qId : sub) {
                    saveTopComments(qCommentsByPost.get(qId), topCommentsPerPost, true);
                }

                // ================= 3. 抓取 Answer Comments =================
                if (!savedAnswerIds.isEmpty()) {
                    int aChunk = 20;
                    for (int k = 0; k < savedAnswerIds.size(); k += aChunk) {
                        List<Long> aSub = savedAnswerIds.subList(k, Math.min(savedAnswerIds.size(), k + aChunk));
                        String aIdsParam = aSub.stream().map(String::valueOf).collect(Collectors.joining(";"));
                        
                        List<ApiComment> candidateAComments = new ArrayList<>();
                        int acPage = 1;
                        boolean acHasMore = true;
                        while (acHasMore && acPage <= 3) {
                            try {
                                URI aCommentsUri = UriComponentsBuilder.fromUriString(String.format("%s/answers/%s/comments", baseUrl, aIdsParam))
                                        .queryParam("page", acPage)
                                        .queryParam("pagesize", 100)
                                        .queryParam("order", "desc")
                                        .queryParam("sort", "votes")
                                        .queryParam("site", "stackoverflow")
                                        .queryParam("key", apiKey)
                                        .queryParam("filter", FILTER_WITH_BODY)
                                        .build()
                                        .toUri();

                                // 修复点：获取 String
                                String json = restTemplate.getForObject(aCommentsUri, String.class);
                                if (json == null) break;

                                JsonNode root = mapper.readTree(json);
                                for (JsonNode node : root.path("items")) {
                                    candidateAComments.add(mapper.treeToValue(node, ApiComment.class));
                                }
                                acHasMore = root.path("has_more").asBoolean(false);
                                int backoff = root.path("backoff").asInt(0);
                                if (backoff > 0) sleep(backoff * 1000L);
                                acPage++;
                            } catch (Exception e) {
                                System.err.println("Error fetching A-comments: " + e.getMessage());
                                break;
                            }
                        }
                        
                        Map<Long, List<ApiComment>> aCommentsByPost = candidateAComments.stream()
                                .filter(c -> c.getPostId() != null)
                                .collect(Collectors.groupingBy(ApiComment::getPostId));
                                
                        for (Long aId : aSub) {
                            saveTopComments(aCommentsByPost.get(aId), topCommentsPerPost, false);
                        }
                    }
                }

                sleep(500L); 

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void saveQuestionFromApi(ApiQuestion a) {
        Question q = questionRepository.findBySoId(a.getQuestionId()).orElseGet(Question::new);
        if (q.getId() == null) q.setSoId(a.getQuestionId());
        
        q.setTitle(a.getTitle());
        q.setBody(a.getBody());
        q.setTags(a.getTags() == null ? null : String.join(",", a.getTags()));
        q.setScore(a.getScore());
        q.setAnswerCount(a.getAnswerCount());
        q.setHasAcceptedAnswer(a.getIsAnswered());
        q.setViewCount(a.getViewCount());
        q.setLink(a.getLink());
        q.setContentLicense(a.getContentLicense());
        
        // 修复：正确保存 API 返回的 comment_count
        q.setQuestionCommentsNumber(a.getCommentCount() != null ? a.getCommentCount() : 0);

        if (a.getCreationDate() != null) {
            q.setCreationDate(LocalDateTime.ofInstant(Instant.ofEpochSecond(a.getCreationDate()), ZoneId.systemDefault()));
        }
        if (a.getLastActivityDate() != null) {
            q.setLastActivityDate(LocalDateTime.ofInstant(Instant.ofEpochSecond(a.getLastActivityDate()), ZoneId.systemDefault()));
        }
        if (a.getLastEditDate() != null) {
            q.setLastEditDate(LocalDateTime.ofInstant(Instant.ofEpochSecond(a.getLastEditDate()), ZoneId.systemDefault()));
        }

        if (a.getOwner() != null && a.getOwner().getUserId() != null) {
            SOUser user = saveOrUpdateUser(a.getOwner());
            q.setOwner(user);
        }
        questionRepository.save(q);
    }

    private void saveTopComments(List<ApiComment> list, int limit, boolean isQuestion) {
        if (list == null || list.isEmpty()) {
            System.err.println("DEBUG: saveTopComments called with empty list");
            return;
        }
        System.err.println("DEBUG: saveTopComments called with " + list.size() + " comments. isQuestion=" + isQuestion);

        list.sort((c1, c2) -> Integer.compare(c2.getScore(), c1.getScore())); // 降序

        // 修复：如果 API 返回的 comment_count 为 0，但实际抓取到了评论，则更新 comment_count
        if (isQuestion) {
            Long postId = list.get(0).getPostId();
            questionRepository.findBySoId(postId).ifPresent(q -> {
                // 强制更新评论数，以实际抓取到的为准（或者取最大值）
                int current = q.getQuestionCommentsNumber() == null ? 0 : q.getQuestionCommentsNumber();
                if (list.size() > current) {
                    q.setQuestionCommentsNumber(list.size());
                    questionRepository.save(q);
                }
            });
        }

        // 寻找最早的评论时间 (用于计算等待时间)

        Long firstCommentTime = list.stream()
                .map(ApiComment::getCreationDate)
                .filter(Objects::nonNull)
                .min(Long::compare)
                .orElse(null);

        int count = 0;
        for (ApiComment ac : list) {
            if (count >= limit) break;
            if (commentRepository.existsBySoId(ac.getCommentId())) continue;

            Comment c = new Comment();
            c.setSoId(ac.getCommentId());
            c.setText(ac.getBody());
            c.setScore(ac.getScore());
            c.setPostId(ac.getPostId());
            
            if (isQuestion) {
                Question q = questionRepository.findBySoId(ac.getPostId()).orElse(null);
                if (q != null) {
                    c.setQuestion(q);
                    // 如果这是该问题的第一条评论，计算并保存等待时间
                    if (firstCommentTime != null && q.getCreationDate() != null) {
                        long qCreationEpoch = q.getCreationDate().atZone(ZoneId.of("UTC")).toEpochSecond();
                        long waitSeconds = firstCommentTime - qCreationEpoch;
                        // 只有当等待时间合理（>=0）时才保存，避免数据异常
                        if (waitSeconds >= 0) {
                            q.setFirstCommentWaitTimeSeconds(waitSeconds);
                            questionRepository.save(q);
                        }
                    }
                }
            } else {
                // 如果是 Answer 的评论，不关联 Question (或者根据你的实体定义关联 Answer)
                // 这里保持 Question 为空，避免外键错误
                c.setQuestion(null); 
                answerRepository.findBySoId(ac.getPostId()).ifPresent(c::setAnswer);
            }

            if (ac.getOwner() != null && ac.getOwner().getUserId() != null) {
                c.setOwner(saveOrUpdateUser(ac.getOwner()));
            }
            
            if (ac.getReplyToUser() != null && ac.getReplyToUser().getUserId() != null) {
                c.setReplyOwnerId(ac.getReplyToUser().getUserId());
            }

            commentRepository.save(c);
            count++;
        }
    }

    // 重载方法：处理 ApiQuestion.Owner
    private SOUser saveOrUpdateUser(ApiQuestion.Owner ownerDto) {
        return saveOrUpdateUserInternal(ownerDto.getUserId(), ownerDto.getDisplay_name(), ownerDto.getReputation(), ownerDto.getLink(), ownerDto.getAccountId());
    }

    // 重载方法：处理 ApiAnswer.Owner
    private SOUser saveOrUpdateUser(ApiAnswer.Owner ownerDto) {
        return saveOrUpdateUserInternal(ownerDto.getUserId(), ownerDto.getDisplay_name(), ownerDto.getReputation(), ownerDto.getLink(), ownerDto.getAccountId());
    }

    // 重载方法：处理 ApiComment.Owner
    private SOUser saveOrUpdateUser(ApiComment.Owner ownerDto) {
        return saveOrUpdateUserInternal(ownerDto.getUserId(), ownerDto.getDisplay_name(), ownerDto.getReputation(), ownerDto.getLink(), ownerDto.getAccountId());
    }

    // 内部通用逻辑
    private SOUser saveOrUpdateUserInternal(Long userId, String displayName, Integer reputation, String link, Long accountId) {
        SOUser user = soUserRepository.findBySoUserId(userId).orElseGet(() -> {
            SOUser u = new SOUser();
            u.setSoUserId(userId);
            return u;
        });
        user.setDisplayName(displayName);
        user.setReputation(reputation);
        user.setLink(link);
        user.setAccountId(accountId);
        return soUserRepository.save(user);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 核心优化策略：按季度分层采样 (2020-2025)
     * 每个季度抓取 100 条：20 条 Top Votes (经典/热门) + 80 条 Random (普通/长尾)
     */
    public void collectQuarterlyBalancedDataset() {
        int startYear = 2020;
        int endYear = 2025;
        int topNPerQuarter = 20;
        int randomNPerQuarter = 80;

        System.out.println("Starting Quarterly Balanced Collection (2020-2025)...");

        for (int year = startYear; year <= endYear; year++) {
            // 每年 4 个季度
            for (int quarter = 1; quarter <= 4; quarter++) {
                // 计算当前季度的起止时间戳
                long fromDate = getQuarterStartEpoch(year, quarter);
                long toDate = getQuarterEndEpoch(year, quarter);
                
                // 如果开始时间超过当前时间，停止抓取 (处理 2025 年未来的季度)
                if (fromDate > Instant.now().getEpochSecond()) {
                    break;
                }

                System.out.println(String.format(">>> Processing %d Q%d (Top: %d, Random: %d)", year, quarter, topNPerQuarter, randomNPerQuarter));

                // 1. 抓取 Top N (按 Votes 排序) - 代表该季度最受关注的问题
                fetchQuestionsByTimeRange(topNPerQuarter, "votes", "java", fromDate, toDate);

                // 2. 抓取 Random N (按 Creation 排序 + 随机页码) - 代表该季度的普通问题分布
                // 注意：这里用 creation 排序模拟随机，通过随机页码实现
                fetchQuestionsByTimeRangeRandomly(randomNPerQuarter, "java", fromDate, toDate);

                // 避免触发 API 速率限制
                sleep(2000); 
            }
        }
        System.out.println("Quarterly Balanced Collection Finished.");
    }

    /**
     * 辅助方法：指定时间范围抓取 (Top N)
     */
    private void fetchQuestionsByTimeRange(int limit, String sort, String tagged, long fromDate, long toDate) {
        // 复用之前的逻辑，但强制使用传入的时间范围
        fetchQuestionsInternal(limit, sort, tagged, fromDate, toDate, false);
    }

    /**
     * 辅助方法：指定时间范围随机抓取 (Random N)
     * 原理：在时间范围内，随机生成页码请求
     */
    private void fetchQuestionsByTimeRangeRandomly(int limit, String tagged, long fromDate, long toDate) {
        // 使用 "random" 模式，内部会处理随机页码
        fetchQuestionsInternal(limit, "random", tagged, fromDate, toDate, true);
    }

    /**
     * 统一的内部抓取实现
     */
    private void fetchQuestionsInternal(int limit, String sort, String tagged, long fromDate, long toDate, boolean isRandomMode) {
        try {
            int pageSizeLocal = 100;
            List<Long> collectedIds = new ArrayList<>();
            int page = 1;
            
            // 如果是随机模式，尝试从前 10 页中随机取，增加随机性
            if (isRandomMode) {
                page = new Random().nextInt(10) + 1; 
            }

            while (collectedIds.size() < limit) {
                // 构建 URL
                UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + "/questions")
                        .queryParam("pagesize", pageSizeLocal)
                        .queryParam("fromdate", fromDate)
                        .queryParam("todate", toDate)
                        .queryParam("order", "desc")
                        .queryParam("site", "stackoverflow")
                        .queryParam("key", apiKey)
                        .queryParam("filter", FILTER_WITH_BODY)
                        .queryParam("tagged", tagged);

                if (isRandomMode) {
                    // 随机模式：按创建时间排序，随机页码
                    builder.queryParam("sort", "creation");
                    builder.queryParam("page", page); 
                    // 下一次循环页码随机跳动
                    page = new Random().nextInt(20) + 1; 
                } else {
                    // Top模式：按指定(votes)排序，页码递增
                    builder.queryParam("sort", sort);
                    builder.queryParam("page", page);
                    page++;
                }

                URI uri = builder.build().toUri();
                
                try {
                    String json = restTemplate.getForObject(uri, String.class);
                    if (json == null) break;
                    
                    JsonNode root = mapper.readTree(json);
                    JsonNode items = root.path("items");
                    if (items.isEmpty()) break;

                    for (JsonNode node : items) {
                        if (collectedIds.size() >= limit) break;
                        ApiQuestion aq = mapper.treeToValue(node, ApiQuestion.class);
                        if (aq == null || aq.getQuestionId() == null) continue;
                        
                        // 查重：如果数据库已存在，跳过（避免不同策略抓到同一个）
                        if (questionRepository.existsBySoId(aq.getQuestionId())) continue;

                        saveQuestionFromApi(aq);
                        collectedIds.add(aq.getQuestionId());
                    }
                    
                    if (!root.path("has_more").asBoolean()) break;
                    
                    int backoff = root.path("backoff").asInt(0);
                    if (backoff > 0) sleep(backoff * 1000L);

                } catch (Exception e) {
                    System.err.println("Error fetching batch: " + e.getMessage());
                    break;
                }
                
                sleep(500); // 批次间短暂休眠
            }

            // 抓取详情
            if (!collectedIds.isEmpty()) {
                fetchTopAnswersAndCommentsForQuestions(collectedIds, 3, 5);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- 时间计算辅助方法 ---

    private long getQuarterStartEpoch(int year, int quarter) {
        int startMonth = (quarter - 1) * 3 + 1;
        return LocalDateTime.of(year, startMonth, 1, 0, 0)
                .atZone(ZoneId.of("UTC")).toEpochSecond();
    }

    private long getQuarterEndEpoch(int year, int quarter) {
        int startMonth = (quarter - 1) * 3 + 1;
        LocalDateTime start = LocalDateTime.of(year, startMonth, 1, 0, 0);
        // 加3个月减1秒，即为季度末
        return start.plusMonths(3).minusSeconds(1)
                .atZone(ZoneId.of("UTC")).toEpochSecond();
    }
}