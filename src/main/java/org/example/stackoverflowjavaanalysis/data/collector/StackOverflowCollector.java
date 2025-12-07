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
}