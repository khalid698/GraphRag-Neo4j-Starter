package com.khalid698.tutorials.codegraph.api.dto;

public record IngestRequest(
        String repoPath,
        String moduleName,
        Options options
) {
    public record Options(
            Boolean includeTests,
            Integer chunkChars,
            Integer overlap,
            Boolean embed
    ) {
    }
}
