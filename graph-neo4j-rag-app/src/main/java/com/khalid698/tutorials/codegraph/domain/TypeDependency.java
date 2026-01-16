package com.khalid698.tutorials.codegraph.domain;

public record TypeDependency(
        String sourceFqcn,
        String targetFqcn,
        String kind,
        String via
) {
}
