package org.example.stackoverflowjavaanalysis.data.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "question_stats", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"topic_id", "stat_date"})
})
public class QuestionStat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate; // 统计时间点（通常存每月1号）

    private int questionCount;      // 问题总数
    private long totalScore;        // 总点赞数
    private long totalAnswers;      // 总回答数
    private long totalComments;     // 总评论数
    private int highQualityCount;   // 优质问题数（如 score > 5）

    public QuestionStat() {}

    public QuestionStat(Topic topic, LocalDate statDate, int questionCount, long totalScore, long totalAnswers, long totalComments, int highQualityCount) {
        this.topic = topic;
        this.statDate = statDate;
        this.questionCount = questionCount;
        this.totalScore = totalScore;
        this.totalAnswers = totalAnswers;
        this.totalComments = totalComments;
        this.highQualityCount = highQualityCount;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public Topic getTopic() { return topic; }
    public LocalDate getStatDate() { return statDate; }
    public int getQuestionCount() { return questionCount; }
    public long getTotalScore() { return totalScore; }
    public long getTotalAnswers() { return totalAnswers; }
    public long getTotalComments() { return totalComments; }
    public int getHighQualityCount() { return highQualityCount; }
}