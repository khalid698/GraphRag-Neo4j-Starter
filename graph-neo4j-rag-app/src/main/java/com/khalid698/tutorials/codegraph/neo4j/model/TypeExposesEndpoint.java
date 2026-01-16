package com.khalid698.tutorials.codegraph.neo4j.model;

public record TypeExposesEndpoint(
        String typeModule,
        String typeFqcn,
        String endpointModule,
        String httpMethod,
        String path
) {
}
