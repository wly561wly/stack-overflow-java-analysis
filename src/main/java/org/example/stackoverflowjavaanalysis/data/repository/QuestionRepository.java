package org.example.stackoverflowjavaanalysis.data.repository;

import org.example.stackoverflowjavaanalysis.data.model.Question;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
    // 按 Stack Overflow 官方 ID 查询（避免重复采集）
    boolean existsBySoId(Long soId);
    Optional<Question> findBySoId(Long soId);

    // 新增：查询Top N高分问题
    // 注意：Spring Data JPA 命名规范支持 findTopNBy... 但有时需要 Pageable
    // 这里使用 Pageable 更通用
    List<Question> findByOrderByScoreDesc(Pageable pageable);

    List<Question> findByCreationDateBetween(LocalDateTime start, LocalDateTime end);

    // 按标签查询
    List<Question> findByTagsContainingAndCreationDateBetween(String tag, LocalDateTime start, LocalDateTime end);

    // 新增：按标题查询
    List<Question> findByTitleContainingAndCreationDateBetween(String title, LocalDateTime start, LocalDateTime end);

    // 新增：按正文查询
    List<Question> findByBodyContainingAndCreationDateBetween(String body, LocalDateTime start, LocalDateTime end);
}
