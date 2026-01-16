package com.khalid698.tutorials.codegraph.neo4j.model;

public record TypeNode(
        String module,
        String fqcn,
        String name,
        String kind,
        String path,
        Integer startLine,
        Integer endLine
) {
}
