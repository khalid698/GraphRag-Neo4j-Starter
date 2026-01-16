package com.khalid698.tutorials.codegraph.api.dto;

public record GraphPathRequest(
        String sourceFqcn,
        String targetFqcn
) {
}
