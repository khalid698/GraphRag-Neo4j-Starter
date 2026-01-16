package com.khalid698.tutorials.codegraph.domain;

public record ChunkDoc(
        String id,
        String moduleName,
        String ownerFqcn,
        String ownerSignature,
        String path,
        Integer startLine,
        Integer endLine,
        String kind,
        String text,
        String textHash
) {
}
