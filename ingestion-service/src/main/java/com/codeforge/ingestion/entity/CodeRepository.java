package com.codeforge.ingestion.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "repositories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeRepository {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String url;

    @Column
    private String branch;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IngestionStatus status;

    @Column(name = "total_files")
    private Integer totalFiles;

    @Column(name = "processed_files")
    private Integer processedFiles;

    @Column(name = "user_email")
    private String userEmail;

    @Column(name = "local_path")
    private String localPath;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = IngestionStatus.PENDING;
        if (totalFiles == null) totalFiles = 0;
        if (processedFiles == null) processedFiles = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum IngestionStatus {
        PENDING, CLONING, PROCESSING, COMPLETED, FAILED
    }
}