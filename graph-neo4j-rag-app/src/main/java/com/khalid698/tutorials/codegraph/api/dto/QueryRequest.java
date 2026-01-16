package com.khalid698.tutorials.codegraph.api.dto;

public record QueryRequest(
        String question,
        String moduleName,
        Integer topK,
        Integer hops,
        Boolean generateAnswer
) {
}
