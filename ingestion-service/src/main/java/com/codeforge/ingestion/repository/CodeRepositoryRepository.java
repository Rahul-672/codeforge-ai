package com.codeforge.ingestion.repository;

import com.codeforge.ingestion.entity.CodeRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CodeRepositoryRepository extends JpaRepository<CodeRepository, String> {

    List<CodeRepository> findByUserEmail(String userEmail);

    boolean existsByUrl(String url);
}