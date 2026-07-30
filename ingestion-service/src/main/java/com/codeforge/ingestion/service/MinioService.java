package com.codeforge.ingestion.service;

import io.minio.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Service
public class MinioService {

    private MinioClient minioClient;
    private final String bucketName;
    private boolean available = false;

    public MinioService(
            @Value("${minio.url:http://localhost:9000}") String url,
            @Value("${minio.access-key:minioadmin}") String accessKey,
            @Value("${minio.secret-key:minioadmin123}") String secretKey,
            @Value("${minio.region:us-east-1}") String region,
            @Value("${minio.bucket:codeforge-repos}") String bucketName) {

        this.bucketName = bucketName;
        try {
            this.minioClient = MinioClient.builder()
                    .endpoint(url)
                    .credentials(accessKey, secretKey)
                    .region(region)
                    .build();

            createBucketIfNotExists();
            this.available = true;
            log.info("MinIO client initialized successfully: {}", url);
        } catch (Exception e) {
            log.warn("MinIO is unavailable at startup ({}). File storage will be disabled. Reason: {}",
                    url, e.getMessage());
        }
    }

    private void createBucketIfNotExists() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucketName).build());
        if (!exists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(bucketName).build());
            log.info("Created MinIO bucket: {}", bucketName);
        } else {
            log.info("MinIO bucket already exists: {}", bucketName);
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public String uploadFile(String objectName, Path filePath) {
        if (!available) {
            log.warn("MinIO is not available. Skipping upload of: {}", objectName);
            throw new RuntimeException("MinIO storage is not available");
        }
        try {
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) contentType = "text/plain";

            minioClient.uploadObject(
                    UploadObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .filename(filePath.toString())
                            .contentType(contentType)
                            .build());

            log.info("Uploaded file to MinIO: {}", objectName);
            return objectName;

        } catch (Exception e) {
            log.error("Error uploading file to MinIO: {}", e.getMessage());
            throw new RuntimeException("Failed to upload file: " + e.getMessage());
        }
    }
}