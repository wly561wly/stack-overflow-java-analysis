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

            System.out.println(String.format("Fetching questions: limit=%d, sort=%s, tags=%s", limit, sort, tagParam));

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
        if (list == null || list.isEmpty()) return;
        list.sort((c1, c2) -> Integer.compare(c2.getScore(), c1.getScore())); // 降序

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
                questionRepository.findBySoId(ac.getPostId()).ifPresent(c::setQuestion);
            } else {
                // 如果是 Answer 的评论，不关联 Question (或者根据你的实体定义关联 Answer)
                // 这里保持 Question 为空，避免外键错误
                c.setQuestion(null); 
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
}