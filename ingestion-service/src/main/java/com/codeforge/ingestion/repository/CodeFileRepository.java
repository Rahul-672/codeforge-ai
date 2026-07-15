package com.codeforge.ingestion.repository;

import com.codeforge.ingestion.entity.CodeFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CodeFileRepository extends JpaRepository<CodeFile, String> {

    List<CodeFile> findByRepositoryId(String repositoryId);

    long countByRepositoryId(String repositoryId);
}