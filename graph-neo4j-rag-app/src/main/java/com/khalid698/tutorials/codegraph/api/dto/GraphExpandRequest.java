package com.khalid698.tutorials.codegraph.api.dto;

import java.util.List;

public record GraphExpandRequest(
        List<String> nodeIds,
        Integer hops
) {
}
