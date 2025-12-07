package org.example.stackoverflowjavaanalysis.data.repository;

import org.example.stackoverflowjavaanalysis.data.model.SOUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SOUserRepository extends JpaRepository<SOUser, Long> {
    Optional<SOUser> findBySoUserId(Long soUserId);
}