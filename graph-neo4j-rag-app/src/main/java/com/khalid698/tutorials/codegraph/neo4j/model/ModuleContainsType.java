package com.khalid698.tutorials.codegraph.neo4j.model;

public record ModuleContainsType(
        String moduleName,
        String typeModule,
        String typeFqcn
) {
}
