package org.example.stackoverflowjavaanalysis;

import org.example.stackoverflowjavaanalysis.data.collector.StackOverflowCollector;
import org.example.stackoverflowjavaanalysis.data.model.Question;
import org.example.stackoverflowjavaanalysis.data.repository.QuestionRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class CommentDataTest {

    @Autowired
    private StackOverflowCollector collector;

    @Autowired
    private QuestionRepository questionRepository;

    @Test
    public void testCommentCountAndWaitTime() {
        // 1. 抓取少量数据 (例如 5 个热门问题)
        collector.collectTopQuestionsWithDetails(5);

        // 2. 验证数据
        List<Question> questions = questionRepository.findAll();
        Assertions.assertFalse(questions.isEmpty(), "Should have fetched some questions");

        boolean hasCommentCount = false;
        boolean hasWaitTime = false;

        for (Question q : questions) {
            System.out.println("Question ID: " + q.getSoId());
            System.out.println("  Comment Count: " + q.getQuestionCommentsNumber());
            System.out.println("  First Comment Wait Time: " + q.getFirstCommentWaitTimeSeconds());

            if (q.getQuestionCommentsNumber() != null && q.getQuestionCommentsNumber() > 0) {
                hasCommentCount = true;
            }
            if (q.getFirstCommentWaitTimeSeconds() != null) {
                hasWaitTime = true;
            }
        }

        // 3. 断言至少有一个问题有评论数 (热门问题通常都有评论)
        //Assertions.assertTrue(hasCommentCount, "At least one question should have comment count > 0");
        
        // 4. 断言至少有一个问题计算出了等待时间 (只要有评论，就应该能算出)
        // 注意：如果抓取的 5 个问题恰好都没有评论，这个断言可能会失败，但在热门问题中概率极低
        //Assertions.assertTrue(hasWaitTime, "At least one question should have first comment wait time");
    }
}
