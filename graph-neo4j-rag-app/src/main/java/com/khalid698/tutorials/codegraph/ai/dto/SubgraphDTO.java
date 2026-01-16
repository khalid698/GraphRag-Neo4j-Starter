package com.khalid698.tutorials.codegraph.ai.dto;

import java.util.List;

public record SubgraphDTO(
        List<NodeDTO> nodes,
        List<RelationshipDTO> relationships
) {
}
