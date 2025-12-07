package org.example.stackoverflowjavaanalysis.data.model;

import jakarta.persistence.*; // changed from javax.persistence.*
import java.util.List;

@Entity
@Table(name = "so_users")
public class SOUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Stack Overflow user id
    private Long soUserId;

    private Long accountId;

    private String displayName;

    private Integer reputation;

    private String link;

    @OneToMany(mappedBy = "owner")
    private List<Question> questions;

    @OneToMany(mappedBy = "owner")
    private List<Answer> answers;

    @OneToMany(mappedBy = "owner")
    private List<Comment> comments;

    public SOUser() {}

    // getters / setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSoUserId() { return soUserId; }
    public void setSoUserId(Long soUserId) { this.soUserId = soUserId; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public Integer getReputation() { return reputation; }
    public void setReputation(Integer reputation) { this.reputation = reputation; }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public List<Question> getQuestions() { return questions; }
    public void setQuestions(List<Question> questions) { this.questions = questions; }

    public List<Answer> getAnswers() { return answers; }
    public void setAnswers(List<Answer> answers) { this.answers = answers; }

    public List<Comment> getComments() { return comments; }
    public void setComments(List<Comment> comments) { this.comments = comments; }
}