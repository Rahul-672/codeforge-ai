package com.codeforge.ingestion.service;

import com.codeforge.ingestion.entity.CodeRepository;
import com.codeforge.ingestion.repository.CodeRepositoryRepository;
import org.springframework.context.ApplicationContext;
import com.codeforge.ingestion.dto.IngestRequest;
import com.codeforge.ingestion.dto.IngestResponse;
import com.codeforge.ingestion.entity.CodeFile;
import com.codeforge.ingestion.repository.CodeFileRepository;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final CodeRepositoryRepository repositoryRepository;
    private final CodeFileRepository codeFileRepository;
    private final MinioService minioService;

    @Value("${github.token:}")
    private String githubToken;

    private static final String CLONE_BASE_PATH = System.getProperty("java.io.tmpdir") + "/codeforge/repos/";

    private final ApplicationContext applicationContext;

    private static final Map<String, String> LANGUAGE_MAP = Map.ofEntries(
            Map.entry(".java", "Java"),
            Map.entry(".py", "Python"),
            Map.entry(".js", "JavaScript"),
            Map.entry(".ts", "TypeScript"),
            Map.entry(".go", "Go"),
            Map.entry(".cpp", "C++"),
            Map.entry(".c", "C"),
            Map.entry(".cs", "C#"),
            Map.entry(".rb", "Ruby"),
            Map.entry(".rs", "Rust"),
            Map.entry(".kt", "Kotlin"),
            Map.entry(".md", "Markdown"),
            Map.entry(".yml", "YAML"),
            Map.entry(".yaml", "YAML"),
            Map.entry(".json", "JSON"),
            Map.entry(".xml", "XML"),
            Map.entry(".sql", "SQL"),
            Map.entry(".sh", "Shell"),
            Map.entry(".html", "HTML"),
            Map.entry(".css", "CSS")
    );

    public IngestResponse startIngestion(IngestRequest request, String userEmail) {

        String repoName = extractRepoName(request.getUrl());

        CodeRepository repository = CodeRepository.builder()
                .name(repoName)
                .url(request.getUrl())
                .branch(request.getBranch())
                .description(request.getDescription())
                .userEmail(userEmail)
                .status(CodeRepository.IngestionStatus.PENDING)
                .build();

        repository = repositoryRepository.save(repository);
        log.info("Created repository record: {}", repository.getId());

        applicationContext.getBean(IngestionService.class)
                .processRepositoryAsync(repository.getId(), request.getUrl(), request.getBranch());

        return IngestResponse.builder()
                .repositoryId(repository.getId())
                .name(repoName)
                .url(request.getUrl())
                .status("PENDING")
                .message("Repository ingestion started. Check status with the repository ID.")
                .build();
    }

    @Async
    public void processRepositoryAsync(String repositoryId, String url, String branch) {
        CodeRepository repository = repositoryRepository.findById(repositoryId).orElseThrow();

        try {
            // Step 1 — Clone
            repository.setStatus(CodeRepository.IngestionStatus.CLONING);
            repositoryRepository.save(repository);

            String localPath = CLONE_BASE_PATH + repositoryId;
            log.info("Cloning repository: {} to {}", url, localPath);

            var cloneCommand = Git.cloneRepository()
                    .setURI(url)
                    .setDirectory(java.nio.file.Path.of(localPath).toFile())
                    .setDepth(1);

// Only set branch if user explicitly specifies a non-default branch
// If null or empty, JGit auto-detects the default branch from remote
            if (branch != null && !branch.isBlank()
                    && !branch.equals("main")
                    && !branch.equals("master")) {
                cloneCommand.setBranch(branch);
            }

            if (githubToken != null && !githubToken.isEmpty()) {
                cloneCommand.setCredentialsProvider(
                        new UsernamePasswordCredentialsProvider(githubToken, ""));
            }

            cloneCommand.call().close();

            repository.setLocalPath(localPath);
            log.info("Repository cloned successfully: {}", repositoryId);

            // Step 2 — Process files
            repository.setStatus(CodeRepository.IngestionStatus.PROCESSING);
            repositoryRepository.save(repository);

            List<CodeFile> codeFiles = scanAndUploadFiles(repositoryId, localPath);
            codeFileRepository.saveAll(codeFiles);

            // Step 3 — Complete
            repository.setStatus(CodeRepository.IngestionStatus.COMPLETED);
            repository.setTotalFiles(codeFiles.size());
            repository.setProcessedFiles(codeFiles.size());
            repositoryRepository.save(repository);

            log.info("Ingestion completed: {} files processed for repo {}",
                    codeFiles.size(), repositoryId);

        } catch (Exception e) {
            log.error("Ingestion failed for repository {}: {}", repositoryId, e.getMessage());
            repository.setStatus(CodeRepository.IngestionStatus.FAILED);
            repository.setErrorMessage(e.getMessage());
            repositoryRepository.save(repository);
        }
    }

    private List<CodeFile> scanAndUploadFiles(String repositoryId, String localPath) throws IOException {
        List<CodeFile> files = new ArrayList<>();
        Path repoPath = Paths.get(localPath);

        Files.walkFileTree(repoPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String fileName = file.getFileName().toString();
                String extension = getExtension(fileName);

                if (shouldSkipFile(file, fileName)) {
                    return FileVisitResult.CONTINUE;
                }

                String language = LANGUAGE_MAP.getOrDefault(extension, "Unknown");
                // Normalize to forward slashes so object keys are consistent across
                // OSes. On Windows Path.toString() yields '\\', which produces invalid
                // flat MinIO/S3 keys (no folder hierarchy).
                String relativePath = repoPath.relativize(file).toString().replace('\\', '/');
                String minioPath = repositoryId + "/" + relativePath;

                try {
                    minioService.uploadFile(minioPath, file);

                    files.add(CodeFile.builder()
                            .repositoryId(repositoryId)
                            .filePath(relativePath)
                            .fileName(fileName)
                            .language(language)
                            .fileSize(attrs.size())
                            .minioPath(minioPath)
                            .build());

                } catch (Exception e) {
                    log.warn("Failed to upload file {}: {}", relativePath, e.getMessage());
                }

                return FileVisitResult.CONTINUE;
            }
        });

        return files;
    }

    private boolean shouldSkipFile(Path file, String fileName) {
        String pathStr = file.toString();
        return pathStr.contains(".git") ||
                pathStr.contains("node_modules") ||
                pathStr.contains("target") ||
                pathStr.contains(".idea") ||
                pathStr.contains("__pycache__") ||
                fileName.endsWith(".class") ||
                fileName.endsWith(".jar") ||
                fileName.endsWith(".png") ||
                fileName.endsWith(".jpg") ||
                fileName.endsWith(".ico");
    }

    private String getExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot) : "";
    }

    private String extractRepoName(String url) {
        String[] parts = url.split("/");
        String name = parts[parts.length - 1];
        return name.endsWith(".git") ? name.substring(0, name.length() - 4) : name;
    }

    public CodeRepository getStatus(String repositoryId) {
        return repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new RuntimeException("Repository not found: " + repositoryId));
    }

    public List<CodeRepository> getUserRepositories(String userEmail) {
        return repositoryRepository.findByUserEmail(userEmail);
    }
}