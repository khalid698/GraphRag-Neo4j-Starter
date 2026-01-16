package com.khalid698.tutorials.codegraph.neo4j.model;

public record TypeDependency(
        String sourceModule,
        String sourceFqcn,
        String targetModule,
        String targetFqcn,
        String kind,
        String via
) {
}
