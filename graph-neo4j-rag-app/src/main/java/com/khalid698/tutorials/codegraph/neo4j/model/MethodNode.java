package com.khalid698.tutorials.codegraph.neo4j.model;

public record MethodNode(
        String module,
        String fqcn,
        String name,
        String signature,
        String returnType,
        String visibility,
        boolean isStatic,
        boolean isAbstract,
        String path,
        Integer startLine,
        Integer endLine
) {
}
