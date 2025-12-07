// filepath: d:\Desktop\stack-overflow-java-analysis\src\main\java\org\example\stackoverflowjavaanalysis\data\model\Comment.java
package org.example.stackoverflowjavaanalysis.data.model;

import jakarta.persistence.*; // changed from javax.persistence.*
import java.time.LocalDateTime;

@Entity
@Table(name = "comments")
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Stack Overflow comment id
    private Long soId;

    // 评论内容
    @Column(columnDefinition = "text")
    private String text;

    // 分数
    private Integer score;

    // 所属帖子 id（可能是 question 或 answer 的 soId）
    private Long postId;

    // 将原来的 reply user id / reply display name 替换为 replyOwnerId
    private Long replyOwnerId;

    // 关联的问题（若评论在问题下）
    @ManyToOne
    @JoinColumn(name = "question_id")
    private Question question;

    // 关联的回答（若评论在回答下）
    @ManyToOne
    @JoinColumn(name = "answer_id")
    private Answer answer;

    // 评论作者
    @ManyToOne
    @JoinColumn(name = "owner_id")
    private SOUser owner;

    public Comment() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSoId() { return soId; }
    public void setSoId(Long soId) { this.soId = soId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }

    public Long getReplyOwnerId() { return replyOwnerId; }
    public void setReplyOwnerId(Long replyOwnerId) { this.replyOwnerId = replyOwnerId; }

    public Question getQuestion() { return question; }
    public void setQuestion(Question question) { this.question = question; }

    public Answer getAnswer() { return answer; }
    public void setAnswer(Answer answer) { this.answer = answer; }

    public SOUser getOwner() { return owner; }
    public void setOwner(SOUser owner) { this.owner = owner; }
}