package com.codeforge.ingestion.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "code_files")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "repository_id", nullable = false)
    private String repositoryId;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column
    private String language;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "minio_path")
    private String minioPath;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}