package org.example.stackoverflowjavaanalysis.data.repository;

import org.example.stackoverflowjavaanalysis.data.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
    // 按 Stack Overflow 官方 ID 查询（避免重复采集）
    boolean existsBySoId(Long soId);
    Optional<Question> findBySoId(Long soId);
}
