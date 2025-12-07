package org.example.stackoverflowjavaanalysis;

import org.example.stackoverflowjavaanalysis.data.collector.StackOverflowCollector;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.example.stackoverflowjavaanalysis.data.repository.AnswerRepository;
import org.example.stackoverflowjavaanalysis.data.repository.CommentRepository;
import org.example.stackoverflowjavaanalysis.data.repository.QuestionRepository;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
public class FullPlanRunTest {

    @Autowired
    private StackOverflowCollector collector;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private AnswerRepository answerRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void testCollectFullPlanRun() {
        commentRepository.deleteAll();
        commentRepository.flush();

        answerRepository.deleteAll();
        answerRepository.flush();

        questionRepository.deleteAll();
        questionRepository.flush();

        try {
            jdbcTemplate.execute("ALTER TABLE questions ALTER COLUMN id RESTART WITH 1");
            jdbcTemplate.execute("ALTER TABLE answers ALTER COLUMN id RESTART WITH 1");
            jdbcTemplate.execute("ALTER TABLE comments ALTER COLUMN id RESTART WITH 1");
        } catch (Exception ignore) {}

        try {
            jdbcTemplate.execute("ALTER SEQUENCE questions_id_seq RESTART WITH 1");
            jdbcTemplate.execute("ALTER SEQUENCE answers_id_seq RESTART WITH 1");
            jdbcTemplate.execute("ALTER SEQUENCE comments_id_seq RESTART WITH 1");
        } catch (Exception ignore) {}

        Assertions.assertDoesNotThrow(() -> collector.collectFullPlan(false));
    }
}