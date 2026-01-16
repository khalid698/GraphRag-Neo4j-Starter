package com.khalid698.tutorials.codegraph.ingest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.khalid698.tutorials.codegraph.domain.Chunk;
import com.khalid698.tutorials.codegraph.domain.MethodDef;

@Service
public class ChunkingService {

    private static final String CHUNK_KIND_METHOD_BODY = "METHOD_BODY";

    private final EmbeddingTextBuilder embeddingTextBuilder;
    private final int defaultChunkSize;
    private final int defaultOverlap;

    public ChunkingService(EmbeddingTextBuilder embeddingTextBuilder,
                           @Value("${app.ingest.default-chunk-chars:800}") int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        this.embeddingTextBuilder = embeddingTextBuilder;
        this.defaultChunkSize = chunkSize;
        this.defaultOverlap = Math.max(0, chunkSize / 5); // simple default overlap (20%)
    }

    public List<Chunk> chunkMethod(MethodDef method, List<String> annotations, String codeSnippet) {
        return chunkMethod(method, annotations, codeSnippet, null, null);
    }

    public List<Chunk> chunkMethod(MethodDef method, List<String> annotations, String codeSnippet, Integer chunkSizeOverride, Integer overlapOverride) {
        int chunkSize = chunkSizeOverride != null && chunkSizeOverride > 0 ? chunkSizeOverride : defaultChunkSize;
        int overlap = overlapOverride != null && overlapOverride >= 0 ? overlapOverride : defaultOverlap;
        String embeddingText = embeddingTextBuilder.build(method, annotations, codeSnippet);
        if (!StringUtils.hasText(embeddingText)) {
            return List.of();
        }

        List<Chunk> chunks = new ArrayList<>();
        int index = 0;
        int start = 0;
        while (start < embeddingText.length()) {
            int end = Math.min(embeddingText.length(), start + chunkSize);
            String chunkText = embeddingText.substring(start, end);
            String textHash = sha256(chunkText);
            String id = sha256(method.moduleName() + "|" + method.declaringTypeFqcn() + "|" + method.signature()
                    + "|" + index + "|" + textHash);

            chunks.add(new Chunk(
                    id,
                    method.moduleName(),
                    method.declaringTypeFqcn(),
                    method.signature(),
                    method.path(),
                    method.startLine(),
                    method.endLine(),
                    CHUNK_KIND_METHOD_BODY,
                    chunkText,
                    textHash
            ));

            if (end == embeddingText.length()) {
                break;
            }
            start = end - overlap;
            index++;
        }
        return chunks;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
