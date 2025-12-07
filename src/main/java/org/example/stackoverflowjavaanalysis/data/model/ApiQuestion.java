package org.example.stackoverflowjavaanalysis.data.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiQuestion {
    @JsonProperty("question_id")
    private Long questionId;

    private String title;
    private String body;
    private List<String> tags;

    @JsonProperty("creation_date")
    private Long creationDate;

    @JsonProperty("last_activity_date")
    private Long lastActivityDate;

    @JsonProperty("last_edit_date")
    private Long lastEditDate;

    @JsonProperty("view_count")
    private Integer viewCount;

    private Integer score;

    @JsonProperty("answer_count")
    private Integer answerCount;

    @JsonProperty("is_answered")
    private Boolean isAnswered;

    @JsonProperty("content_license")
    private String contentLicense;

    private String link;

    private Owner owner;

    // 新增：API 返回的 comment_count 字段
    @JsonProperty("comment_count")
    private Integer commentCount;

    public Integer getCommentCount() { return commentCount; }
    public void setCommentCount(Integer commentCount) { this.commentCount = commentCount; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Owner {
        @JsonProperty("user_id")
        private Long userId;

        @JsonProperty("account_id")
        private Long accountId;

        private String display_name;
        private Integer reputation;
        private String profile_image;
        private String link;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Long getAccountId() { return accountId; }
        public void setAccountId(Long accountId) { this.accountId = accountId; }
        public String getDisplay_name() { return display_name; }
        public void setDisplay_name(String display_name) { this.display_name = display_name; }
        public Integer getReputation() { return reputation; }
        public void setReputation(Integer reputation) { this.reputation = reputation; }
        public String getProfile_image() { return profile_image; }
        public void setProfile_image(String profile_image) { this.profile_image = profile_image; }
        public String getLink() { return link; }
        public void setLink(String link) { this.link = link; }
    }

    // getters / setters (驼峰命名，供 Collector 调用)
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public Long getCreationDate() { return creationDate; }
    public void setCreationDate(Long creationDate) { this.creationDate = creationDate; }
    public Long getLastActivityDate() { return lastActivityDate; }
    public void setLastActivityDate(Long lastActivityDate) { this.lastActivityDate = lastActivityDate; }
    public Long getLastEditDate() { return lastEditDate; }
    public void setLastEditDate(Long lastEditDate) { this.lastEditDate = lastEditDate; }
    public Integer getViewCount() { return viewCount; }
    public void setViewCount(Integer viewCount) { this.viewCount = viewCount; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public Integer getAnswerCount() { return answerCount; }
    public void setAnswerCount(Integer answerCount) { this.answerCount = answerCount; }
    public Boolean getIsAnswered() { return isAnswered; }
    public void setIsAnswered(Boolean isAnswered) { this.isAnswered = isAnswered; }
    public String getContentLicense() { return contentLicense; }
    public void setContentLicense(String contentLicense) { this.contentLicense = contentLicense; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
    public Owner getOwner() { return owner; }
    public void setOwner(Owner owner) { this.owner = owner; }
}