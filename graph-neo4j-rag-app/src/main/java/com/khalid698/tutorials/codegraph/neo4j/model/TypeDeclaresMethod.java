package com.khalid698.tutorials.codegraph.neo4j.model;

public record TypeDeclaresMethod(
        String typeModule,
        String typeFqcn,
        String methodModule,
        String methodFqcn,
        String signature
) {
}
