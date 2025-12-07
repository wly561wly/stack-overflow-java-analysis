// filepath: d:\Desktop\stack-overflow-java-analysis\src\main\java\org\example\stackoverflowjavaanalysis\data\model\ApiComment.java
package org.example.stackoverflowjavaanalysis.data.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiComment {
    @JsonProperty("comment_id")
    private Long commentId;

    @JsonProperty("post_id")
    private Long postId;

    private String body;

    @JsonProperty("creation_date")
    private Long creationDate;

    private Integer score;

    @JsonProperty("reply_to_user")
    private ReplyToUser replyToUser;

    private Owner owner;

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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReplyToUser {
        @JsonProperty("user_id")
        private Long userId;
        private String display_name;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getDisplay_name() { return display_name; }
        public void setDisplay_name(String display_name) { this.display_name = display_name; }
    }

    public Long getCommentId() { return commentId; }
    public void setCommentId(Long commentId) { this.commentId = commentId; }
    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Long getCreationDate() { return creationDate; }
    public void setCreationDate(Long creationDate) { this.creationDate = creationDate; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public ReplyToUser getReplyToUser() { return replyToUser; }
    public void setReplyToUser(ReplyToUser replyToUser) { this.replyToUser = replyToUser; }
    public Owner getOwner() { return owner; }
    public void setOwner(Owner owner) { this.owner = owner; }
}