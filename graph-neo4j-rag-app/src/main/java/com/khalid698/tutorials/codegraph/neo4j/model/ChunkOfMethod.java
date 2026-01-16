package com.khalid698.tutorials.codegraph.neo4j.model;

public record ChunkOfMethod(
        String chunkId,
        String methodModule,
        String methodFqcn,
        String signature
) {
}
