// filepath: d:\Desktop\stack-overflow-java-analysis\src\main\java\org\example\stackoverflowjavaanalysis\data\repository\CommentRepository.java
package org.example.stackoverflowjavaanalysis.data.repository;

import org.example.stackoverflowjavaanalysis.data.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    boolean existsBySoId(Long soId);
}