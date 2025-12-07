package org.example.stackoverflowjavaanalysis;

import org.example.stackoverflowjavaanalysis.data.collector.StackOverflowCollector;
import org.example.stackoverflowjavaanalysis.data.repository.AnswerRepository;
import org.example.stackoverflowjavaanalysis.data.repository.CommentRepository;
import org.example.stackoverflowjavaanalysis.data.repository.QuestionRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
public class SamplePlanTest {

    @Autowired
    private StackOverflowCollector collector;

    @Autowired
    private AnswerRepository answerRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void testCollectSamplePlan() {
        // 清空表
        commentRepository.deleteAll();
        commentRepository.flush();

        answerRepository.deleteAll();
        answerRepository.flush();

        questionRepository.deleteAll();
        questionRepository.flush();

        // 可选：重置自增序列（根据你使用的 DB 选择合适语句）
        try {
            // H2:
            jdbcTemplate.execute("ALTER TABLE questions ALTER COLUMN id RESTART WITH 1");
            jdbcTemplate.execute("ALTER TABLE answers ALTER COLUMN id RESTART WITH 1");
            jdbcTemplate.execute("ALTER TABLE comments ALTER COLUMN id RESTART WITH 1");
        } catch (Exception ignore) {}

        try {
            // PostgreSQL (如果使用 PostgreSQL，请确保序列名称正确)
            jdbcTemplate.execute("ALTER SEQUENCE questions_id_seq RESTART WITH 1");
            jdbcTemplate.execute("ALTER SEQUENCE answers_id_seq RESTART WITH 1");
            jdbcTemplate.execute("ALTER SEQUENCE comments_id_seq RESTART WITH 1");
        } catch (Exception ignore) {}

        // 不强制断言具体数值，仅确保方法能执行（网络与 API 配额限制可能导致具体值波动）
        Assertions.assertDoesNotThrow(() -> collector.collectSamplePlan());
        long qCount = questionRepository.count();
        System.out.println("SamplePlanTest: question count after run = " + qCount);
        Assertions.assertTrue(qCount >= 100, "Expect at least 100 questions after sample run");
    }
}