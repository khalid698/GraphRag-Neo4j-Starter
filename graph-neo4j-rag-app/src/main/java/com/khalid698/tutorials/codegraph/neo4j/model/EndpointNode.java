package com.khalid698.tutorials.codegraph.neo4j.model;

public record EndpointNode(
        String module,
        String httpMethod,
        String path
) {
}
