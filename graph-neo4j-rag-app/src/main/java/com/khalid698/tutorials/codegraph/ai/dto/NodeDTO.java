package com.khalid698.tutorials.codegraph.ai.dto;

import java.util.Map;

public record NodeDTO(
        String id,
        String label,
        Map<String, Object> properties
) {
}
