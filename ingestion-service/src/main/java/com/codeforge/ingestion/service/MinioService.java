package com.codeforge.ingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;
import java.nio.file.Path;

/**
 * Storage service backed by any S3-compatible endpoint.
 * Uses AWS SDK v2 which supports full URL path overrides (required for Supabase Storage).
 * The MinIO SDK was replaced because it rejects path-based endpoints like:
 *   https://<ref>.storage.supabase.co/storage/v1/s3
 */
@Slf4j
@Service
public class MinioService {

    private S3Client s3Client;
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
            this.s3Client = S3Client.builder()
                    .endpointOverride(URI.create(url))          // accepts full path URLs ✓
                    .region(Region.of(region))
                    .credentialsProvider(
                            StaticCredentialsProvider.create(
                                    AwsBasicCredentials.create(accessKey, secretKey)))
                    .forcePathStyle(true)                       // required for Supabase S3
                    .build();

            ensureBucketExists();
            this.available = true;
            log.info("S3 storage client initialized successfully: {}", url);
        } catch (Exception e) {
            log.warn("S3 storage unavailable at startup ({}). File storage will be disabled. Reason: {}",
                    url, e.getMessage());
        }
    }

    private void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            log.info("S3 bucket already exists: {}", bucketName);
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
            log.info("Created S3 bucket: {}", bucketName);
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public String uploadFile(String objectName, Path filePath) {
        if (!available) {
            log.warn("S3 storage is not available. Skipping upload of: {}", objectName);
            throw new RuntimeException("S3 storage is not available");
        }
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectName)
                            .build(),
                    filePath);

            log.info("Uploaded file to S3 storage: {}", objectName);
            return objectName;

        } catch (Exception e) {
            log.error("Error uploading file to S3 storage: {}", e.getMessage());
            throw new RuntimeException("Failed to upload file: " + e.getMessage());
        }
    }
}