package org.example.stackoverflowjavaanalysis.data.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "questions")
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Stack Overflow question id
    private Long soId;

    private String title;

    @Column(columnDefinition = "text")
    private String body;

    private String tags; // CSV 或其他格式

    private Integer score;

    private Integer answerCount;

    private Boolean hasAcceptedAnswer;

    private Integer viewCount;

    private String contentLicense;

    private String link;

    private LocalDateTime creationDate;
    private LocalDateTime lastActivityDate;
    private LocalDateTime lastEditDate;

    // 删除冗余的 owner reputation、owner account id、link、image、name、user id 字段
    // 保留与 SOUser 的关联（若之前存在 owner 嵌入字段，请删除那些属性）
    @ManyToOne
    @JoinColumn(name = "owner_id")
    private SOUser owner;

    // 新增：问题的评论数量统计字段
    private Integer questionCommentsNumber = 0;

    @OneToMany(mappedBy = "question", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<Comment> comments;

    @OneToMany(mappedBy = "question", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<Answer> answers;

    public Question() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSoId() { return soId; }
    public void setSoId(Long soId) { this.soId = soId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public Integer getAnswerCount() { return answerCount; }
    public void setAnswerCount(Integer answerCount) { this.answerCount = answerCount; }

    public Boolean getHasAcceptedAnswer() { return hasAcceptedAnswer; }
    public void setHasAcceptedAnswer(Boolean hasAcceptedAnswer) { this.hasAcceptedAnswer = hasAcceptedAnswer; }

    public Integer getViewCount() { return viewCount; }
    public void setViewCount(Integer viewCount) { this.viewCount = viewCount; }

    public String getContentLicense() { return contentLicense; }
    public void setContentLicense(String contentLicense) { this.contentLicense = contentLicense; }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public LocalDateTime getCreationDate() { return creationDate; }
    public void setCreationDate(LocalDateTime creationDate) { this.creationDate = creationDate; }

    public LocalDateTime getLastActivityDate() { return lastActivityDate; }
    public void setLastActivityDate(LocalDateTime lastActivityDate) { this.lastActivityDate = lastActivityDate; }

    public LocalDateTime getLastEditDate() { return lastEditDate; }
    public void setLastEditDate(LocalDateTime lastEditDate) { this.lastEditDate = lastEditDate; }

    public SOUser getOwner() { return owner; }
    public void setOwner(SOUser owner) { this.owner = owner; }

    public Integer getQuestionCommentsNumber() { return questionCommentsNumber; }
    public void setQuestionCommentsNumber(Integer questionCommentsNumber) { this.questionCommentsNumber = questionCommentsNumber; }

    public List<Comment> getComments() { return comments; }
    public void setComments(List<Comment> comments) { this.comments = comments; }

    public List<Answer> getAnswers() { return answers; }
    public void setAnswers(List<Answer> answers) { this.answers = answers; }
}