package com.khalid698.tutorials.codegraph.api.dto;

public record IngestResponse(
        String moduleName,
        Counts counts,
        long durationMs
) {
    public record Counts(
            int types,
            int methods,
            int endpoints,
            int chunks,
            int relationships
    ) {
    }
}
