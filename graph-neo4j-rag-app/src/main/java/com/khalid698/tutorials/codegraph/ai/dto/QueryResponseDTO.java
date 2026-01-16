package com.khalid698.tutorials.codegraph.ai.dto;

import java.util.List;

public record QueryResponseDTO(
        String answer,
        List<HitDTO> hits,
        SubgraphDTO subgraph
) {
    public static QueryResponseDTO withoutAnswer(List<HitDTO> hits, SubgraphDTO subgraph) {
        return new QueryResponseDTO(null, hits, subgraph);
    }
}
