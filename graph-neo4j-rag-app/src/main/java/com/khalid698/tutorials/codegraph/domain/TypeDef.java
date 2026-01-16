package com.khalid698.tutorials.codegraph.domain;

public record TypeDef(
        String moduleName,
        String fqcn,
        String name,
        String kind,
        String path,
        Integer startLine,
        Integer endLine
) {
}
