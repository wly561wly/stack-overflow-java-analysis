// filepath: d:\Desktop\stack-overflow-java-analysis\src\main\java\org\example\stackoverflowjavaanalysis\data\model\ApiAnswer.java
package org.example.stackoverflowjavaanalysis.data.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiAnswer {
    @JsonProperty("answer_id")
    private Long answerId;

    @JsonProperty("question_id")
    private Long questionId;

    private String body;

    @JsonProperty("creation_date")
    private Long creationDate;

    @JsonProperty("last_edit_date")
    private Long lastEditDate;

    private Integer score;

    @JsonProperty("is_accepted")
    private Boolean isAccepted;

    @JsonProperty("content_license")
    private String contentLicense;

    private Owner owner;

    // 新增：API 返回的 comment_count 字段
    @JsonProperty("comment_count")
    private Integer commentCount;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Owner {
        @JsonProperty("user_id")
        private Long userId;
        @JsonProperty("account_id")
        private Long accountId;
        private String display_name;
        private Integer reputation;
        private String link;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Long getAccountId() { return accountId; }
        public void setAccountId(Long accountId) { this.accountId = accountId; }
        public String getDisplay_name() { return display_name; }
        public void setDisplay_name(String display_name) { this.display_name = display_name; }
        public Integer getReputation() { return reputation; }
        public void setReputation(Integer reputation) { this.reputation = reputation; }
        public String getLink() { return link; }
        public void setLink(String link) { this.link = link; }
    }

    public Long getAnswerId() { return answerId; }
    public void setAnswerId(Long answerId) { this.answerId = answerId; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Long getCreationDate() { return creationDate; }
    public void setCreationDate(Long creationDate) { this.creationDate = creationDate; }
    public Long getLastEditDate() { return lastEditDate; }
    public void setLastEditDate(Long lastEditDate) { this.lastEditDate = lastEditDate; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public Boolean getIsAccepted() { return isAccepted; }
    public void setIsAccepted(Boolean isAccepted) { this.isAccepted = isAccepted; }
    public String getContentLicense() { return contentLicense; }
    public void setContentLicense(String contentLicense) { this.contentLicense = contentLicense; }
    public Owner getOwner() { return owner; }
    public void setOwner(Owner owner) { this.owner = owner; }

    public Integer getCommentCount() { return commentCount; }
    public void setCommentCount(Integer commentCount) { this.commentCount = commentCount; }
}