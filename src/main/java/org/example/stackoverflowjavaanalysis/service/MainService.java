package org.example.stackoverflowjavaanalysis.service;

import org.example.stackoverflowjavaanalysis.data.model.Answer;
import org.example.stackoverflowjavaanalysis.data.model.Question;
import org.example.stackoverflowjavaanalysis.data.repository.AnswerRepository;
import org.example.stackoverflowjavaanalysis.data.repository.QuestionRepository;
import org.example.stackoverflowjavaanalysis.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MainService {
    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private AnswerRepository answerRepository;

    public List<QuestionDTO> getTopScoreQuestions(int topN) {
        List<Question> topQuestions = questionRepository.findByOrderByScoreDesc(PageRequest.of(0, topN));
        
        return topQuestions.stream()
                .map(question -> {
                    String truncatedTitle = StringUtils.truncateLongContent(question.getTitle());
                    String truncatedBody = StringUtils.truncateLongContent(question.getBody());
                    
                    List<AnswerDTO> topAnswers = getTopAnswersByQuestionId(question.getSoId(), 2);
                    
                    return new QuestionDTO(
                            question.getSoId(),
                            truncatedTitle,
                            truncatedBody,
                            question.getTitle(), // 完整标题
                            question.getBody(),  // 完整内容
                            question.getScore() == null ? 0 : question.getScore(),
                            question.getAnswerCount() == null ? 0 : question.getAnswerCount(),
                            question.getHasAcceptedAnswer() != null && question.getHasAcceptedAnswer(),
                            topAnswers
                    );
                })
                .collect(Collectors.toList());
    }

    private List<AnswerDTO> getTopAnswersByQuestionId(Long questionSoId, int topN) {
        List<Answer> topAnswers = answerRepository.findByQuestionSoIdOrderByScoreDesc(questionSoId, PageRequest.of(0, topN));
        
        return topAnswers.stream()
                .map(answer -> new AnswerDTO(
                        answer.getSoId(),
                        StringUtils.truncateLongContent(answer.getBody()),
                        answer.getScore() == null ? 0 : answer.getScore(),
                        answer.getIsAccepted() != null && answer.getIsAccepted()
                ))
                .collect(Collectors.toList());
    }

    public record QuestionDTO(
            Long soId,
            String truncatedTitle,
            String truncatedBody,
            String fullTitle,
            String fullBody,
            int score,
            int answerCount,
            boolean hasAcceptedAnswer,
            List<AnswerDTO> topAnswers
    ) {}

    public record AnswerDTO(
            Long soId,
            String truncatedBody,
            int score,
            boolean isAccepted
    ) {}
}