package com.codeforge.ingestion.rag.embedding;

import com.codeforge.ingestion.rag.chunk.CodeChunk;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.*;
import io.qdrant.client.grpc.Points.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;

@Slf4j
@Service
public class QdrantService {

    private static final String COLLECTION_NAME = "codeforge_chunks";
    private static final int VECTOR_SIZE = 768; // voyage ai dim

    private QdrantClient client;

    @Value("${qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${qdrant.port:6334}")
    private int qdrantPort;

    @PostConstruct
    public void init() {
        try {
            client = new QdrantClient(
                    QdrantGrpcClient.newBuilder(qdrantHost, qdrantPort, false)
                            .build());
            createCollectionIfNotExists();
            log.info("Qdrant client initialized at {}:{}", qdrantHost, qdrantPort);
        } catch (Exception e) {
            log.error("Failed to initialize Qdrant client: {}", e.getMessage());
        }
    }

    private void createCollectionIfNotExists() {
        try {
            var collections = client.listCollectionsAsync().get();
            boolean exists = collections.stream()
                    .anyMatch(c -> c.equals(COLLECTION_NAME));

            if (!exists) {
                client.createCollectionAsync(
                        COLLECTION_NAME,
                        VectorParams.newBuilder()
                                .setSize(VECTOR_SIZE)
                                .setDistance(Distance.Cosine)
                                .build()).get();
                log.info("Created Qdrant collection: {}", COLLECTION_NAME);
            }
        } catch (Exception e) {
            log.error("Error creating Qdrant collection: {}", e.getMessage());
        }
    }

    public void storeChunk(CodeChunk chunk, List<Float> embedding) {
        try {
            var point = PointStruct.newBuilder()
                    .setId(id(UUID.fromString(chunk.getId())))
                    .setVectors(vectors(embedding))
                    .putAllPayload(Map.of(
                            "content", value(chunk.getContent()),
                            "repository_id", value(chunk.getRepositoryId()),
                            "file_path", value(chunk.getFilePath()),
                            "file_name", value(chunk.getFileName()),
                            "language", value(chunk.getLanguage()),
                            "method_name", value(chunk.getMethodName() != null
                                    ? chunk.getMethodName() : ""),
                            "chunk_index", value(chunk.getChunkIndex()),
                            "chunk_type", value(chunk.getChunkType())
                    ))
                    .build();

            client.upsertAsync(COLLECTION_NAME, List.of(point)).get();

        } catch (Exception e) {
            log.error("Error storing chunk in Qdrant: {}", e.getMessage());
        }
    }

    public List<ScoredPoint> search(List<Float> queryVector,
                                    String repositoryId,
                                    int topK) {
        try {
            var filter = Filter.newBuilder()
                    .addMust(Condition.newBuilder()
                            .setField(FieldCondition.newBuilder()
                                    .setKey("repository_id")
                                    .setMatch(Match.newBuilder()
                                            .setKeyword(repositoryId)
                                            .build())
                                    .build())
                            .build())
                    .build();

            return client.searchAsync(
                    SearchPoints.newBuilder()
                            .setCollectionName(COLLECTION_NAME)
                            .addAllVector(queryVector)
                            .setFilter(filter)
                            .setLimit(topK)
                            .setWithPayload(WithPayloadSelector.newBuilder()
                                    .setEnable(true)
                                    .build())
                            .build()).get();

        } catch (Exception e) {
            log.error("Error searching Qdrant: {}", e.getMessage());
            return List.of();
        }
    }

    public void deleteByRepositoryId(String repositoryId) {
        try {
            var filter = Filter.newBuilder()
                    .addMust(Condition.newBuilder()
                            .setField(FieldCondition.newBuilder()
                                    .setKey("repository_id")
                                    .setMatch(Match.newBuilder()
                                            .setKeyword(repositoryId)
                                            .build())
                                    .build())
                            .build())
                    .build();

            client.deleteAsync(COLLECTION_NAME, filter).get();
            log.info("Deleted vectors for repository: {}", repositoryId);
        } catch (Exception e) {
            log.error("Error deleting from Qdrant: {}", e.getMessage());
        }
    }
}