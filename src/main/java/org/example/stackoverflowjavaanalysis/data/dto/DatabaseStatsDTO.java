package org.example.stackoverflowjavaanalysis.data.dto;

public class DatabaseStatsDTO {
    private long questionCount;
    private long answerCount;
    private long commentCount;
    private long userCount;

    public DatabaseStatsDTO(long questionCount, long answerCount, long commentCount, long userCount) {
        this.questionCount = questionCount;
        this.answerCount = answerCount;
        this.commentCount = commentCount;
        this.userCount = userCount;
    }

    public long getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(long questionCount) {
        this.questionCount = questionCount;
    }

    public long getAnswerCount() {
        return answerCount;
    }

    public void setAnswerCount(long answerCount) {
        this.answerCount = answerCount;
    }

    public long getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(long commentCount) {
        this.commentCount = commentCount;
    }

    public long getUserCount() {
        return userCount;
    }

    public void setUserCount(long userCount) {
        this.userCount = userCount;
    }
}
