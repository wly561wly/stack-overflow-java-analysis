// filepath: d:\Desktop\stack-overflow-java-analysis\src\main\java\org\example\stackoverflowjavaanalysis\data\repository\AnswerRepository.java
package org.example.stackoverflowjavaanalysis.data.repository;

import org.example.stackoverflowjavaanalysis.data.model.Answer;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnswerRepository extends JpaRepository<Answer, Long> {
    boolean existsBySoId(Long soId);

    // 新增：查询某问题的Top N回答
    List<Answer> findByQuestionSoIdOrderByScoreDesc(Long questionSoId, Pageable pageable);
}