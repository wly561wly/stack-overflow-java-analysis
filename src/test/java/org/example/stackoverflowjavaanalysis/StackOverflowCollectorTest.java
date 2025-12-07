package org.example.stackoverflowjavaanalysis;

import org.example.stackoverflowjavaanalysis.data.collector.StackOverflowCollector;
import org.example.stackoverflowjavaanalysis.data.repository.AnswerRepository;
import org.example.stackoverflowjavaanalysis.data.repository.CommentRepository;
import org.example.stackoverflowjavaanalysis.data.repository.QuestionRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class StackOverflowCollectorTest {

    @Autowired
    private StackOverflowCollector collector;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private AnswerRepository answerRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Test
    public void testCollectTop10QuestionsWithDetails() {
        
        commentRepository.deleteAll();
        commentRepository.flush();

        answerRepository.deleteAll();
        answerRepository.flush();

        questionRepository.deleteAll();
        questionRepository.flush();

        collector.collectTopQuestionsWithDetails(10);

        long qCount = questionRepository.count();
        long aCount = answerRepository.count();
        long cCount = commentRepository.count();

        System.out.println("Questions: " + qCount + ", Answers: " + aCount + ", Comments: " + cCount);

        Assertions.assertTrue(qCount >= 10, "Expected at least 10 questions");
        Assertions.assertTrue(aCount >= 1, "Expected at least 1 answer");
        Assertions.assertTrue(cCount >= 1, "Expected at least 1 comment");
    }
}