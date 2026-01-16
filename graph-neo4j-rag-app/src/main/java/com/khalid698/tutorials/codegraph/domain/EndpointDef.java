package com.khalid698.tutorials.codegraph.domain;

public record EndpointDef(
        String moduleName,
        String httpMethod,
        String path,
        String implementingTypeFqcn,
        String implementingSignature
) {
}
