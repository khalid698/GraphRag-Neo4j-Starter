package com.khalid698.tutorials.codegraph.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.khalid698.tutorials.codegraph.domain.ChunkDoc;
import com.khalid698.tutorials.codegraph.neo4j.GraphWriter;
import com.khalid698.tutorials.codegraph.neo4j.Neo4jClient;
import com.khalid698.tutorials.codegraph.neo4j.model.ChunkNode;
import com.khalid698.tutorials.codegraph.neo4j.model.UpsertResult;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final GraphWriter graphWriter;
    private final Neo4jClient neo4jClient;
    private final String embeddingModelName;

    public EmbeddingService(EmbeddingModel embeddingModel,
                            EmbeddingStore<TextSegment> embeddingStore,
                            GraphWriter graphWriter,
                            Neo4jClient neo4jClient,
                            @Value("${app.embedding-model:text-embedding-3-small}") String embeddingModelName) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.graphWriter = graphWriter;
        this.neo4jClient = neo4jClient;
        this.embeddingModelName = embeddingModelName;
    }

    public UpsertResult embedAndPersistChunks(List<ChunkDoc> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return UpsertResult.empty();
        }

        log.info("Starting embedding for {} chunk docs", chunks.size());

        List<ChunkNode> chunkNodes = new ArrayList<>();
        List<Embedding> embeddings = new ArrayList<>();
        List<TextSegment> segments = new ArrayList<>();
        for (ChunkDoc chunk : chunks) {
            if (!StringUtils.hasText(chunk.text())) {
                continue;
            }

            ExistingChunk existing = findExisting(chunk.id());
            List<Double> embeddingVector = null;
            Embedding embeddingObj = null;
            boolean reused = false;

            if (existing != null
                    && embeddingModelName.equals(existing.embeddingModel())
                    && chunk.textHash().equals(existing.textHash())
                    && existing.embedding() != null) {
                embeddingVector = existing.embedding();
                embeddingObj = fromList(embeddingVector);
                reused = true;
            } else {
                try {
                    embeddingObj = embeddingModel.embed(chunk.text()).content();
                    embeddingVector = toVector(embeddingObj);
                } catch (Exception e) {
                    log.info("Failed to embed chunk {} (module={}, owner={}, sig={}): {}", chunk.id(), chunk.moduleName(), chunk.ownerFqcn(), chunk.ownerSignature(), e.getMessage());
                }
            }
            log.info("Add new chunk ChunkNode: {}", chunk.id());
            chunkNodes.add(new ChunkNode(
                    chunk.id(),
                    chunk.moduleName(),
                    chunk.ownerFqcn(),
                    chunk.ownerSignature(),
                    chunk.path(),
                    chunk.startLine(),
                    chunk.endLine(),
                    chunk.kind(),
                    chunk.text(),
                    chunk.textHash(),
                    embeddingModelName,
                    embeddingVector
            ));

            if (embeddingVector != null && embeddingObj != null && !reused) {
                embeddings.add(embeddingObj);

                Map<String, Object> meta = new HashMap<>();
                if (chunk.id() != null) meta.put("id", chunk.id());
                if (chunk.moduleName() != null) meta.put("module", chunk.moduleName());
                if (chunk.ownerFqcn() != null) meta.put("ownerFqcn", chunk.ownerFqcn());
                if (chunk.ownerSignature() != null) meta.put("ownerSignature", chunk.ownerSignature());
                if (chunk.path() != null) meta.put("path", chunk.path());
                if (chunk.startLine() != null) meta.put("startLine", chunk.startLine());
                if (chunk.endLine() != null) meta.put("endLine", chunk.endLine());
                if (chunk.kind() != null) meta.put("kind", chunk.kind());
                if (chunk.textHash() != null) meta.put("textHash", chunk.textHash());
                if (embeddingModelName != null) meta.put("embeddingModel", embeddingModelName);
                try {
                    segments.add(TextSegment.from(chunk.text(), Metadata.from(meta)));
                    log.info("Add new segment for chunk {}", chunk.id());
                } catch (Exception e) {
                    log.info("Failed to create TextSegment for chunk {}: {}", chunk.id(), e.getMessage());
                }
                
                if (!reused) {
                    log.info("Embedded chunk {} with vector size {}", chunk.id(), embeddingObj.vector() != null ? embeddingObj.vector().length : -1);
                }
            } else {
                log.info("Skipping embedding persistence for chunk {} (module={}, owner={}, sig={}) because embedding was null",
                        chunk.id(), chunk.moduleName(), chunk.ownerFqcn(), chunk.ownerSignature());
            }
        }

        if (chunkNodes.isEmpty()) {
        	log.info("No chunks produced for ingestion; skipping embedding and upsert");
            return UpsertResult.empty();
        }

        UpsertResult upsertResult = graphWriter.upsertChunks(chunkNodes);
        log.info("Upserted {} chunks (created={}, updated={})", chunkNodes.size(), upsertResult.created(), upsertResult.updated());
        if (!embeddings.isEmpty() && !segments.isEmpty()) {
            try {
                embeddingStore.addAll(embeddings, segments);
                log.info("Stored {} embeddings in embedding store", embeddings.size());
            } catch (Exception e) {
                log.info("Failed to store embeddings for {} segments: {}", segments.size(), e.getMessage());
            }
        }
        return upsertResult;
    }

    private ExistingChunk findExisting(String id) {
        if (!StringUtils.hasText(id)) {
            return null;
        }
        List<Map<String, Object>> rows = neo4jClient.executeRead(
                "MATCH (c:Chunk {id: $id}) RETURN c.textHash AS textHash, c.embeddingModel AS embeddingModel, c.embedding AS embedding",
                Map.of("id", id));

        if (rows == null || rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        String textHash = Optional.ofNullable(row.get("textHash")).map(Object::toString).orElse(null);
        String model = Optional.ofNullable(row.get("embeddingModel")).map(Object::toString).orElse(null);
        Object embeddingVal = row.get("embedding");
        List<Double> embedding = toVector(embeddingVal);

        return new ExistingChunk(textHash, model, embedding);
    }

    @SuppressWarnings("unchecked")
    private List<Double> toVector(Object embeddingVal) {
        if (embeddingVal == null) {
            return null;
        }
        if (embeddingVal instanceof List<?> list) {
            return list.stream()
                    .filter(Number.class::isInstance)
                    .map(Number.class::cast)
                    .map(Number::doubleValue)
                    .toList();
        }
        if (embeddingVal instanceof dev.langchain4j.data.embedding.Embedding embedding) {
            List<Float> vector = embedding.vectorAsList();
            return vector == null ? null : vector.stream().map(Float::doubleValue).collect(Collectors.toList());
        }
        return null;
    }

    private Embedding fromList(List<Double> embeddingVector) {
        if (embeddingVector == null) {
            return null;
        }
        float[] arr = new float[embeddingVector.size()];
        for (int i = 0; i < embeddingVector.size(); i++) {
            arr[i] = embeddingVector.get(i).floatValue();
        }
        return Embedding.from(arr);
    }

    private record ExistingChunk(String textHash, String embeddingModel, List<Double> embedding) {
    }
}
