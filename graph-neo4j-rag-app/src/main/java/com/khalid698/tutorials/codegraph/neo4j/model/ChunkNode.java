package com.khalid698.tutorials.codegraph.neo4j.model;

import java.util.List;

public record ChunkNode(
        String id,
        String module,
        String ownerFqcn,
        String ownerSignature,
        String path,
        Integer startLine,
        Integer endLine,
        String kind,
        String text,
        String textHash,
        String embeddingModel,
        List<Double> embedding
) {
}
