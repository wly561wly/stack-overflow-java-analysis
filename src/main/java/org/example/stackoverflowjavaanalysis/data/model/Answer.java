// filepath: d:\Desktop\stack-overflow-java-analysis\src\main\java\org\example\stackoverflowjavaanalysis\data\model\Answer.java
package org.example.stackoverflowjavaanalysis.data.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "answers")
public class Answer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long soId;

    @Column(columnDefinition = "text")
    private String body;

    private Integer score;

    private Boolean isAccepted;

    private String contentLicense;

    private LocalDateTime lastEditDate;

    // 新增：答案下的评论数量统计字段
    private Integer answerCommentsNumber = 0;

    @ManyToOne
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @OneToMany(mappedBy = "answer", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<Comment> comments;

    @ManyToOne
    @JoinColumn(name = "owner_id")
    private SOUser owner;

    public Answer() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSoId() { return soId; }
    public void setSoId(Long soId) { this.soId = soId; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public Boolean getIsAccepted() { return isAccepted; }
    public void setIsAccepted(Boolean accepted) { isAccepted = accepted; }

    public String getContentLicense() { return contentLicense; }
    public void setContentLicense(String contentLicense) { this.contentLicense = contentLicense; }

    public LocalDateTime getLastEditDate() { return lastEditDate; }
    public void setLastEditDate(LocalDateTime lastEditDate) { this.lastEditDate = lastEditDate; }

    public Integer getAnswerCommentsNumber() { return answerCommentsNumber; }
    public void setAnswerCommentsNumber(Integer answerCommentsNumber) { this.answerCommentsNumber = answerCommentsNumber; }

    public Question getQuestion() { return question; }
    public void setQuestion(Question question) { this.question = question; }

    public List<Comment> getComments() { return comments; }
    public void setComments(List<Comment> comments) { this.comments = comments; }

    public SOUser getOwner() { return owner; }
    public void setOwner(SOUser owner) { this.owner = owner; }
}