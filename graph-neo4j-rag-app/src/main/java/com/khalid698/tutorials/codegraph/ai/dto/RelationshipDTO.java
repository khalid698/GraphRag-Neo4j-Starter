package com.khalid698.tutorials.codegraph.ai.dto;

import java.util.Map;

public record RelationshipDTO(
        String id,
        String type,
        String sourceId,
        String targetId,
        Map<String, Object> properties
) {
}
