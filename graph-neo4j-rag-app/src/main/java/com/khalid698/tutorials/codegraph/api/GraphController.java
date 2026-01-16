package com.khalid698.tutorials.codegraph.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.khalid698.tutorials.codegraph.api.dto.GraphExpandRequest;
import com.khalid698.tutorials.codegraph.api.dto.GraphPathRequest;
import com.khalid698.tutorials.codegraph.ai.dto.SubgraphDTO;
import com.khalid698.tutorials.codegraph.ai.dto.NodeDTO;
import com.khalid698.tutorials.codegraph.ai.dto.RelationshipDTO;
import com.khalid698.tutorials.codegraph.neo4j.CypherTemplates;
import com.khalid698.tutorials.codegraph.neo4j.Neo4jClient;

@RestController
@RequestMapping("/api/v1/graph")
public class GraphController {

    private final Neo4jClient neo4jClient;
    private final CypherTemplates templates;

    public GraphController(Neo4jClient neo4jClient, CypherTemplates templates) {
        this.neo4jClient = neo4jClient;
        this.templates = templates;
    }

    @PostMapping("/expand")
    public ResponseEntity<SubgraphDTO> expand(@RequestBody GraphExpandRequest request) {
        if (request == null || request.nodeIds() == null || request.nodeIds().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        int hops = request.hops() != null ? request.hops() : 2;
        String cypher = templates.load("cypher/graphExpandFromIds.cypher");
        var rows = neo4jClient.executeRead(cypher, java.util.Map.of("ids", request.nodeIds(), "hops", hops));
        if (rows.isEmpty()) {
            return ResponseEntity.ok(new SubgraphDTO(java.util.List.of(), java.util.List.of()));
        }
        var row = rows.get(0);
        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> nodesRaw = (java.util.List<java.util.Map<String, Object>>) row.getOrDefault("nodes", java.util.List.of());
        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> relsRaw = (java.util.List<java.util.Map<String, Object>>) row.getOrDefault("rels", java.util.List.of());
        SubgraphDTO subgraph = new SubgraphDTO(
                nodesRaw.stream().map(m -> new NodeDTO(
                        m.get("id").toString(),
                        m.getOrDefault("label", "Node").toString(),
                        (java.util.Map<String, Object>) m.getOrDefault("properties", java.util.Map.of())
                )).toList(),
                relsRaw.stream().map(m -> new RelationshipDTO(
                        m.get("id").toString(),
                        m.getOrDefault("type", "REL").toString(),
                        m.getOrDefault("sourceId", "").toString(),
                        m.getOrDefault("targetId", "").toString(),
                        (java.util.Map<String, Object>) m.getOrDefault("properties", java.util.Map.of())
                )).toList()
        );
        return ResponseEntity.ok(subgraph);
    }

    @PostMapping("/path")
    public ResponseEntity<Object> path(@RequestBody GraphPathRequest request) {
        if (request == null || request.sourceFqcn() == null || request.targetFqcn() == null) {
            return ResponseEntity.badRequest().build();
        }
        String cypher = templates.load("cypher/shortestPathTypesSimple.cypher");
        var rows = neo4jClient.executeRead(cypher, java.util.Map.of(
                "sourceFqcn", request.sourceFqcn(),
                "targetFqcn", request.targetFqcn()));
        return ResponseEntity.ok(rows);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handle(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
    }
}
