// filepath: d:\Desktop\stack-overflow-java-analysis\src\main\java\org\example\stackoverflowjavaanalysis\data\collector\StackOverflowCollector.java
package org.example.stackoverflowjavaanalysis.data.collector;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.stackoverflowjavaanalysis.data.model.*;
import org.example.stackoverflowjavaanalysis.data.repository.AnswerRepository;
import org.example.stackoverflowjavaanalysis.data.repository.CommentRepository;
import org.example.stackoverflowjavaanalysis.data.repository.QuestionRepository;
import org.example.stackoverflowjavaanalysis.data.repository.SOUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.File;
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

    // 你原来的 collectQuestions() 保留（可选），这里省略...

    /**
     * 为测试用：一次性抓取 limit 条“热门”问题及其 answers / comments
     */
    public void collectTopQuestionsWithDetails(int limit) {
        try {
            int pageSizeLocal = Math.min(limit, 100);
            String url = String.format(
                    "%s/questions?page=1&pagesize=%d&order=desc&sort=votes&tagged=%s&site=stackoverflow&key=%s&filter=withbody",
                    baseUrl, pageSizeLocal, tags, apiKey);

            String json = restTemplate.getForObject(url, String.class);
            if (json == null) return;

            // 保存原始 questions 响应
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File("api_top_questions.json"), mapper.readTree(json));

            StackOverflowResponse resp = mapper.readValue(json, StackOverflowResponse.class);
            if (resp == null || resp.getItems() == null) return;

            List<ApiQuestion> apiQuestions = resp.getItems();
            // 只取前 limit 条
            apiQuestions = apiQuestions.stream().limit(limit).collect(Collectors.toList());

            // 先保存 Question
            Map<Long, Question> questionMap = new HashMap<>();
            for (ApiQuestion a : apiQuestions) {
                if (a.getQuestionId() == null) continue;
                Long soId = a.getQuestionId();

                Question q = questionRepository.findBySoId(soId).orElseGet(Question::new);
                if (q.getId() == null) {
                    q.setSoId(soId);
                }
                q.setTitle(a.getTitle());
                q.setBody(a.getBody());
                q.setTags(a.getTags() == null ? null : String.join(",", a.getTags()));
                if (a.getCreationDate() != null) {
                    q.setCreationDate(LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(a.getCreationDate()), ZoneId.systemDefault()));
                }
                if (a.getLastActivityDate() != null) {
                    q.setLastActivityDate(LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(a.getLastActivityDate()), ZoneId.systemDefault()));
                }
                // 在保存 Question 的地方，增加赋值：
                q.setScore(a.getScore());
                q.setAnswerCount(a.getAnswerCount());
                q.setHasAcceptedAnswer(a.getIsAnswered());
                q.setViewCount(a.getViewCount());
                q.setContentLicense(a.getContentLicense());
                q.setLink(a.getLink());
                if (a.getLastEditDate() != null) {
                    q.setLastEditDate(LocalDateTime.ofInstant(Instant.ofEpochSecond(a.getLastEditDate()), ZoneId.systemDefault()));
                }
                // ...然后保存 q

                // 处理问题的拥有者信息
                if (a.getOwner() != null && a.getOwner().getUserId() != null) {
                    Long ownerUserId = a.getOwner().getUserId();
                    SOUser user = soUserRepository.findBySoUserId(ownerUserId).orElseGet(() -> {
                        SOUser u = new SOUser();
                        u.setSoUserId(ownerUserId);
                        return u;
                    });
                    // 更新用户字段（冗余更新安全）
                    user.setAccountId(a.getOwner().getAccountId());
                    user.setDisplayName(a.getOwner().getDisplay_name());
                    user.setReputation(a.getOwner().getReputation());
                    user.setLink(a.getOwner().getLink());
                    soUserRepository.save(user);

                    q.setOwner(user);
                }
                questionRepository.save(q);
                questionMap.put(soId, q);
            }

            if (questionMap.isEmpty()) return;

            // 组装 question ids 用于 /answers /comments 接口
            String idsParam = questionMap.keySet().stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(";"));

            // ========= 抓 answers =========
            String answersUrl = String.format(
                    "%s/questions/%s/answers?order=desc&sort=votes&site=stackoverflow&key=%s&filter=withbody",
                    baseUrl, idsParam, apiKey);

            String ansJson = restTemplate.getForObject(answersUrl, String.class);
            if (ansJson != null) {
                mapper.writerWithDefaultPrettyPrinter()
                        .writeValue(new File("api_top_answers.json"), mapper.readTree(ansJson));

                JsonNode root = mapper.readTree(ansJson);
                JsonNode items = root.get("items");
                if (items != null && items.isArray()) {
                    for (JsonNode node : items) {
                        ApiAnswer aa = mapper.treeToValue(node, ApiAnswer.class);
                        if (aa.getAnswerId() == null || aa.getQuestionId() == null) continue;
                        if (!questionMap.containsKey(aa.getQuestionId())) continue;
                        if (answerRepository.existsBySoId(aa.getAnswerId())) continue;

                        Answer ans = new Answer();
                        ans.setSoId(aa.getAnswerId());
                        ans.setBody(aa.getBody());
                        // 在保存 Answer 的地方，增加赋值：
                        ans.setScore(aa.getScore());
                        ans.setIsAccepted(aa.getIsAccepted());
                        ans.setContentLicense(aa.getContentLicense());
                        if (aa.getLastEditDate() != null) {
                            ans.setLastEditDate(LocalDateTime.ofInstant(Instant.ofEpochSecond(aa.getLastEditDate()), ZoneId.systemDefault()));
                        }
                        ans.setQuestion(questionMap.get(aa.getQuestionId()));

                        // 处理回答的拥有者信息
                        if (aa.getOwner() != null && aa.getOwner().getUserId() != null) {
                            Long ownerUserId = aa.getOwner().getUserId();
                            SOUser user = soUserRepository.findBySoUserId(ownerUserId).orElseGet(() -> {
                                SOUser u = new SOUser();
                                u.setSoUserId(ownerUserId);
                                return u;
                            });
                            // 更新用户字段（冗余更新安全）
                            user.setAccountId(aa.getOwner().getAccountId());
                            user.setDisplayName(aa.getOwner().getDisplay_name());
                            user.setReputation(aa.getOwner().getReputation());
                            user.setLink(aa.getOwner().getLink());
                            soUserRepository.save(user);

                            ans.setOwner(user);
                        }
                        answerRepository.save(ans);
                    }
                }
            }

            // ========= 抓 comments =========
            String commentsUrl = String.format(
                    "%s/questions/%s/comments?order=desc&sort=creation&site=stackoverflow&key=%s&filter=withbody",
                    baseUrl, idsParam, apiKey);

            String comJson = restTemplate.getForObject(commentsUrl, String.class);
            if (comJson != null) {
                mapper.writerWithDefaultPrettyPrinter()
                        .writeValue(new File("api_top_comments.json"), mapper.readTree(comJson));

                JsonNode root = mapper.readTree(comJson);
                JsonNode items = root.get("items");
                if (items != null && items.isArray()) {
                    for (JsonNode node : items) {
                        ApiComment ac = mapper.treeToValue(node, ApiComment.class);
                        if (ac.getCommentId() == null || ac.getPostId() == null) continue;
                        if (!questionMap.containsKey(ac.getPostId())) continue;
                        if (commentRepository.existsBySoId(ac.getCommentId())) continue;

                        Comment c = new Comment();
                        c.setSoId(ac.getCommentId());
                        c.setText(ac.getBody());
                        c.setScore(ac.getScore());
                        c.setPostId(ac.getPostId());
                        if (ac.getReplyToUser() != null) {
                            Long replyToUserId = ac.getReplyToUser().getUserId();
                            c.setReplyOwnerId(replyToUserId);
                        }
                        if (ac.getPostId() != null && questionMap.containsKey(ac.getPostId())) {
                            c.setQuestion(questionMap.get(ac.getPostId()));
                        }

                        // 处理评论的拥有者信息
                        if (ac.getOwner() != null && ac.getOwner().getUserId() != null) {
                            Long ownerUserId = ac.getOwner().getUserId();
                            SOUser user = soUserRepository.findBySoUserId(ownerUserId).orElseGet(() -> {
                                SOUser u = new SOUser();
                                u.setSoUserId(ownerUserId);
                                return u;
                            });
                            // 更新用户字段（冗余更新安全）
                            user.setAccountId(ac.getOwner().getAccountId());
                            user.setDisplayName(ac.getOwner().getDisplay_name());
                            user.setReputation(ac.getOwner().getReputation());
                            user.setLink(ac.getOwner().getLink());
                            soUserRepository.save(user);

                            c.setOwner(user);
                        }
                        commentRepository.save(c);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 按指定参数抓取问题（只抓 Question 并保存，pagesize=100，循环直到达到 limit 或无更多）
     * 用于小规模样本验证（不会主动抓取 answers/comments，快速验证 API/Filter/存储流程）
     */
    public void fetchQuestionsByParams(int limit, String sort, String tagged) {
        try {
            int page = 1;
            int pageSizeLocal = Math.min(100, Math.max(1, pageSize));
            List<ApiQuestion> collected = new ArrayList<>();
            boolean hasMore = true;
            String tagParam = (tagged == null || tagged.isBlank()) ? this.tags : tagged;

            while (collected.size() < limit && hasMore) {
                // 不对 tags 编码（保留分号分隔），并对 random 做近似处理
                String tagForUrl = tagParam;
                String sortParam = "random".equalsIgnoreCase(sort) ? "creation" : sort;
                int requestPage = page;
                if ("random".equalsIgnoreCase(sort)) {
                    int maxPage = 100; // 可调整
                    requestPage = new Random().nextInt(maxPage) + 1;
                }

                String url = String.format("%s/questions?page=%d&pagesize=%d&order=desc&sort=%s&tagged=%s&site=stackoverflow&key=%s",
                        baseUrl, requestPage, pageSizeLocal, sortParam, tagForUrl, apiKey);

                System.out.println("DEBUG: fetchQuestionsByParams request URL = " + url);

                String json = restTemplate.getForObject(url, String.class);
                if (json == null) break;

                File out = new File(String.format("api_sample_%s_page_%d.json", sort, page));
                mapper.writerWithDefaultPrettyPrinter().writeValue(out, mapper.readTree(json));

                JsonNode root = mapper.readTree(json);
                JsonNode items = root.path("items");
                if (items == null || !items.isArray() || items.size() == 0) {
                    System.out.println("DEBUG: no items returned for page " + page + " sort=" + sort);
                    break;
                }

                for (JsonNode node : items) {
                    ApiQuestion aq = mapper.treeToValue(node, ApiQuestion.class);
                    if (aq == null || aq.getQuestionId() == null) continue;
                    collected.add(aq);
                    Question q = questionRepository.findBySoId(aq.getQuestionId()).orElseGet(Question::new);
                    if (q.getId() == null) q.setSoId(aq.getQuestionId());
                    q.setTitle(aq.getTitle());
                    q.setBody(aq.getBody());
                    q.setTags(aq.getTags() == null ? null : String.join(",", aq.getTags()));
                    if (aq.getCreationDate() != null) {
                        q.setCreationDate(LocalDateTime.ofInstant(Instant.ofEpochSecond(aq.getCreationDate()), ZoneId.systemDefault()));
                    }
                    // 补充映射：score/answerCount/isAnswered/viewCount/contentLicense/lastEditDate/link
                    q.setScore(aq.getScore());
                    q.setAnswerCount(aq.getAnswerCount());
                    q.setHasAcceptedAnswer(aq.getIsAnswered());
                    q.setViewCount(aq.getViewCount());
                    q.setContentLicense(aq.getContentLicense());
                    q.setLink(aq.getLink());
                    if (aq.getLastEditDate() != null) {
                        q.setLastEditDate(LocalDateTime.ofInstant(Instant.ofEpochSecond(aq.getLastEditDate()), ZoneId.systemDefault()));
                    }
                    // 补充 owner 映射（与 collectTopQuestionsWithDetails 相同）
                    if (aq.getOwner() != null && aq.getOwner().getUserId() != null) {
                        Long ownerUserId = aq.getOwner().getUserId();
                        SOUser user = soUserRepository.findBySoUserId(ownerUserId).orElseGet(() -> {
                            SOUser u = new SOUser();
                            u.setSoUserId(ownerUserId);
                            return u;
                        });
                        user.setAccountId(aq.getOwner().getAccountId());
                        user.setDisplayName(aq.getOwner().getDisplay_name());
                        user.setReputation(aq.getOwner().getReputation());
                        user.setLink(aq.getOwner().getLink());
                        soUserRepository.save(user);
                        q.setOwner(user);
                    }
                    questionRepository.save(q);
                    if (collected.size() >= limit) break;
                }

                hasMore = root.path("has_more").asBoolean(false);
                page++;
                try { Thread.sleep(200L); } catch (InterruptedException ignored) {}
            }

            System.out.println(String.format("fetchQuestionsByParams finished: sort=%s, tagged=%s, collected=%d", sort, tagged, collected.size()));

            // 新：抓取并保存每个 question 的前 2 个 answer（按 score）与每个 post 的前 5 个 comment（按 score）
            if (!collected.isEmpty()) {
                List<Long> qIds = collected.stream().map(ApiQuestion::getQuestionId).filter(Objects::nonNull).collect(Collectors.toList());
                fetchTopAnswersAndCommentsForQuestions(qIds, 2, 5);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 新增 helper：对给定 question id 列表，保存每题前 topAnswersPerQuestion 个 answer（按 score）
    // 并为每个 question/answer 保存前 topCommentsPerPost 个 comment（按 score）
    private void fetchTopAnswersAndCommentsForQuestions(List<Long> questionIds, int topAnswersPerQuestion, int topCommentsPerPost) {
        if (questionIds == null || questionIds.isEmpty()) return;
        try {
            // 分块（每块最多 100 个 id）
            int chunk = 100;
            for (int i = 0; i < questionIds.size(); i += chunk) {
                List<Long> sub = questionIds.subList(i, Math.min(questionIds.size(), i + chunk));
                String idsParam = sub.stream().map(String::valueOf).collect(Collectors.joining(";"));

                // 1) 获取 answers（按 votes 排序），然后按 question_id 分组并只保存 top N per question
                String answersUrl = String.format("%s/questions/%s/answers?order=desc&sort=votes&site=stackoverflow&key=%s&filter=withbody",
                        baseUrl, idsParam, apiKey);
                System.out.println("DEBUG: fetching answers URL = " + answersUrl);
                String ansJson = restTemplate.getForObject(answersUrl, String.class);
                Map<Long, List<ApiAnswer>> byQuestion = new HashMap<>();
                if (ansJson != null) {
                    JsonNode root = mapper.readTree(ansJson);
                    JsonNode items = root.path("items");
                    if (items != null && items.isArray()) {
                        for (JsonNode node : items) {
                            ApiAnswer aa = mapper.treeToValue(node, ApiAnswer.class);
                            if (aa == null || aa.getAnswerId() == null || aa.getQuestionId() == null) continue;
                            byQuestion.computeIfAbsent(aa.getQuestionId(), k -> new ArrayList<>()).add(aa);
                        }
                    }
                }

                // 保存 top answers per question
                List<Long> savedAnswerIds = new ArrayList<>();
                for (Map.Entry<Long, List<ApiAnswer>> e : byQuestion.entrySet()) {
                    List<ApiAnswer> list = e.getValue();
                    list.sort(Comparator.comparingInt(a -> - (a.getScore() == null ? 0 : a.getScore()))); // 按 score 降序
                    int take = Math.min(topAnswersPerQuestion, list.size());
                    for (int j = 0; j < take; j++) {
                        ApiAnswer aa = list.get(j);
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
                        // 关联 question（在 DB 中应存在）
                        questionRepository.findBySoId(aa.getQuestionId()).ifPresent(ans::setQuestion);
                        answerRepository.save(ans);
                        savedAnswerIds.add(ans.getSoId());
                    }
                }

                // 2) 对 question 本身的 comments，保存前 topCommentsPerPost 个（按 score）
                String qCommentsUrl = String.format("%s/questions/%s/comments?order=desc&sort=votes&site=stackoverflow&key=%s&filter=withbody",
                        baseUrl, idsParam, apiKey);
                System.out.println("DEBUG: fetching question comments URL = " + qCommentsUrl);
                String qComJson = restTemplate.getForObject(qCommentsUrl, String.class);
                if (qComJson != null) {
                    JsonNode root = mapper.readTree(qComJson);
                    JsonNode items = root.path("items");
                    Map<Long, List<ApiComment>> commentsByPost = new HashMap<>();
                    if (items != null && items.isArray()) {
                        for (JsonNode node : items) {
                            ApiComment ac = mapper.treeToValue(node, ApiComment.class);
                            if (ac == null || ac.getCommentId() == null || ac.getPostId() == null) continue;
                            commentsByPost.computeIfAbsent(ac.getPostId(), k -> new ArrayList<>()).add(ac);
                        }
                    }
                    // 保存 top comments for questions
                    for (Long qid : sub) {
                        List<ApiComment> cl = commentsByPost.getOrDefault(qid, Collections.emptyList());
                        cl.sort(Comparator.comparingInt(c -> - (c.getScore() == null ? 0 : c.getScore())));
                        int take = Math.min(topCommentsPerPost, cl.size());
                        for (int t = 0; t < take; t++) {
                            ApiComment ac = cl.get(t);
                            if (commentRepository.existsBySoId(ac.getCommentId())) continue;
                            Comment c = new Comment();
                            c.setSoId(ac.getCommentId());
                            c.setText(ac.getBody());
                            c.setScore(ac.getScore());
                            c.setPostId(ac.getPostId());
                            questionRepository.findBySoId(ac.getPostId()).ifPresent(c::setQuestion);
                            commentRepository.save(c);
                        }
                    }
                }

                // 3) 对 answers 的 comments，针对 savedAnswerIds 分块获取并保存 top comments
                if (!savedAnswerIds.isEmpty()) {
                    for (int k = 0; k < savedAnswerIds.size(); k += chunk) {
                        List<Long> aChunk = savedAnswerIds.subList(k, Math.min(savedAnswerIds.size(), k + chunk));
                        String aIdsParam = aChunk.stream().map(String::valueOf).collect(Collectors.joining(";"));
                        String aComUrl = String.format("%s/answers/%s/comments?order=desc&sort=votes&site=stackoverflow&key=%s&filter=withbody",
                                baseUrl, aIdsParam, apiKey);
                        System.out.println("DEBUG: fetching answer comments URL = " + aComUrl);
                        String aComJson = restTemplate.getForObject(aComUrl, String.class);
                        if (aComJson == null) continue;
                        JsonNode root = mapper.readTree(aComJson);
                        JsonNode items = root.path("items");
                        Map<Long, List<ApiComment>> commentsByPost = new HashMap<>();
                        if (items != null && items.isArray()) {
                            for (JsonNode node : items) {
                                ApiComment ac = mapper.treeToValue(node, ApiComment.class);
                                if (ac == null || ac.getCommentId() == null || ac.getPostId() == null) continue;
                                commentsByPost.computeIfAbsent(ac.getPostId(), x -> new ArrayList<>()).add(ac);
                            }
                        }
                        for (Long aid : aChunk) {
                            List<ApiComment> cl = commentsByPost.getOrDefault(aid, Collections.emptyList());
                            cl.sort(Comparator.comparingInt(c -> - (c.getScore() == null ? 0 : c.getScore())));
                            int take = Math.min(topCommentsPerPost, cl.size());
                            // 为 answer 的 comment 设置关联（需要 Answer 实体存在）
                            Optional<Answer> optAns = answerRepository.findAll().stream().filter(x -> aid.equals(x.getSoId())).findFirst();
                            for (int t = 0; t < take; t++) {
                                ApiComment ac = cl.get(t);
                                if (commentRepository.existsBySoId(ac.getCommentId())) continue;
                                Comment c = new Comment();
                                c.setSoId(ac.getCommentId());
                                c.setText(ac.getBody());
                                c.setScore(ac.getScore());
                                c.setPostId(ac.getPostId());
                                if (optAns.isPresent()) c.setQuestion(null); // 保持 question 为空
                                // 需要为 Comment 关联 Answer（若实体有该字段）——此处假定数据库模型用 postId 区分
                                commentRepository.save(c);
                            }
                        }
                    }
                }

                // 小睡防止速率限制
                try { Thread.sleep(200L); } catch (InterruptedException ignored) {}
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * 小规模样本计划：三种模式各抓取 perMode 条（不覆盖现有策略）
     * popular: sort=votes, tagged=java
     * random:  sort=random, tagged=java
     * scenario: sort=activity, tagged=java;multithreading
     */
    public void collectSamplePlan() {
        int perMode = 50; // 默认 50，可在测试时修改此值
        System.out.println("collectSamplePlan: start sample run, perMode=" + perMode);
        // 1) popular
        fetchQuestionsByParams(perMode, "votes", "java");
        // 2) random
        fetchQuestionsByParams(perMode, "random", "java");
        // 3) scenario (multithreading)
        fetchQuestionsByParams(perMode, "activity", "java;multithreading");
        System.out.println("collectSamplePlan: finished sample run");
    }

    // 新增：完整计划入口，dryRun=true 时仅打印计划；dryRun=false 时按三种模式实际抓取
    public void collectFullPlan(boolean dryRun) {
        int popular = 3000;   // 热门目标
        int random = 1500;    // 随机目标
        int scenario = 500;   // 场景化目标（multithreading）
        System.out.println("collectFullPlan: plan -> popular=" + popular + ", random=" + random + ", scenario=" + scenario + ", dryRun=" + dryRun);
        if (dryRun) {
            System.out.println("collectFullPlan: dry run - no network requests will be performed");
            return;
        }

        // 1) 热门：按 votes 抓取 popular 条
        System.out.println("collectFullPlan: fetching popular questions (" + popular + ")");
        fetchQuestionsByParams(popular, "votes", "java");
        // 稍作暂停以避开速率限制
        try { Thread.sleep(10_000L); } catch (InterruptedException ignored) {}

        // 2) 随机：近似随机抽样抓取 random 条
        System.out.println("collectFullPlan: fetching random questions (" + random + ")");
        fetchQuestionsByParams(random, "random", "java");
        try { Thread.sleep(10_000L); } catch (InterruptedException ignored) {}

        // 3) 场景化：并发相关标签抓取 scenario 条
        System.out.println("collectFullPlan: fetching scenario questions (" + scenario + ")");
        fetchQuestionsByParams(scenario, "activity", "java;multithreading");
        System.out.println("collectFullPlan: finished full plan");
    }
}