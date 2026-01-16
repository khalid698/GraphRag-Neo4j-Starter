package com.khalid698.tutorials.codegraph.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.khalid698.tutorials.codegraph.ai.dto.HitDTO;
import com.khalid698.tutorials.codegraph.ai.dto.NodeDTO;
import com.khalid698.tutorials.codegraph.ai.dto.QueryResponseDTO;
import com.khalid698.tutorials.codegraph.ai.dto.RelationshipDTO;
import com.khalid698.tutorials.codegraph.ai.dto.SubgraphDTO;
import com.khalid698.tutorials.codegraph.neo4j.CypherTemplates;
import com.khalid698.tutorials.codegraph.neo4j.Neo4jClient;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;

@Service
public class QueryService {
	private static final Logger log = LoggerFactory.getLogger(QueryService.class);

    private static final String EXPAND_TEMPLATE = "cypher/expandFromChunks.cypher";

    private final EmbeddingModel embeddingModel;
    private final OpenAiChatModel chatModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final Neo4jClient neo4jClient;
    private final CypherTemplates templates;

    public QueryService(EmbeddingModel embeddingModel,
    					OpenAiChatModel chatModel,
                        EmbeddingStore<TextSegment> embeddingStore,
                        Neo4jClient neo4jClient,
                        CypherTemplates templates) {
        this.embeddingModel = embeddingModel;
        this.chatModel = chatModel;
        this.embeddingStore = embeddingStore;
        this.neo4jClient = neo4jClient;
        this.templates = templates;
    }

    public QueryResponseDTO query(String question, String module, int topK, int hops, boolean generateAnswer) {
        if (!StringUtils.hasText(question)) {
            return QueryResponseDTO.withoutAnswer(List.of(), new SubgraphDTO(List.of(), List.of()));
        }

        Embedding queryEmbedding = embeddingModel.embed(question).content();
        List<HitDTO> hits = semanticSearch(queryEmbedding, module, topK);
        SubgraphDTO subgraph = expandGraph(hits, hops);

        if (generateAnswer && chatModel != null) {
            String answer = synthesizeAnswer(question, hits, subgraph);
            return new QueryResponseDTO(answer, hits, subgraph);
        }

        return QueryResponseDTO.withoutAnswer(hits, subgraph);
    }

    private List<HitDTO> semanticSearch(Embedding embedding, String module, int topK) {
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(topK)
                .build();

        var searchResult = embeddingStore.search(request);
        List<EmbeddingMatch<TextSegment>> matches = searchResult == null ? List.of() : searchResult.matches();
        log.info("Semantic search returned {} raw matches", matches.size());
        if (CollectionUtils.isEmpty(matches)) {
            log.info("Semantic search returned no matches");
            return List.of();
        }

        List<HitDTO> hits = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : matches) {
            Object metadataObj = match.embedded() != null ? match.embedded().metadata() : null;
            Map<String, Object> meta = extractMetadata(metadataObj);
            if (meta == null) {
                log.info("Skipping match with missing/invalid metadata");
                continue;
            }
            if (StringUtils.hasText(module)) {
                Object modVal = meta.get("module");
                if (modVal == null || !module.equals(modVal.toString())) {
                    log.info("Skipping match {} due to module mismatch (expected {}, found {})", meta.getOrDefault("id", ""), module, modVal);
                    continue;
                }
            }
            NodeDTO node = new NodeDTO(
                    meta.getOrDefault("id", "").toString(),
                    "Chunk",
                    meta
            );
            hits.add(new HitDTO(match.score(), node));
        }
        log.info("Semantic search produced {} hits after filtering", hits.size());
        return hits;
    }

    private Map<String, Object> extractMetadata(Object metadataObj) {
        if (metadataObj == null) {
            return null;
        }
        if (metadataObj instanceof Map<?, ?> raw) {
            Map<String, Object> meta = new HashMap<>();
            raw.forEach((k, v) -> {
                if (k != null) {
                    meta.put(k.toString(), v);
                }
            });
            return meta;
        }
        if (metadataObj instanceof Metadata m) {
            Map<String, Object> meta = new HashMap<>();
            m.toMap().forEach(meta::put);
            return meta;
        }
        return null;
    }

    private SubgraphDTO expandGraph(List<HitDTO> hits, int hops) {
        if (CollectionUtils.isEmpty(hits)) {
            return new SubgraphDTO(List.of(), List.of());
        }
        List<String> chunkIds = hits.stream()
                .map(HitDTO::node)
                .map(NodeDTO::properties)
                .map(props -> props.get("id"))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .filter(id -> !id.isBlank())
                .toList();

        log.info("Expanding graph for {} chunk ids", chunkIds.size());
        if (chunkIds.isEmpty()) {
            log.info("No chunk ids available to expand");
            return new SubgraphDTO(List.of(), List.of());
        }

        String cypher = templates.load(EXPAND_TEMPLATE);
        List<Map<String, Object>> rows = neo4jClient.executeRead(cypher, Map.of(
                "chunkIds", chunkIds,
                "hops", hops
        ));

        if (CollectionUtils.isEmpty(rows)) {
            return new SubgraphDTO(List.of(), List.of());
        }
        Map<String, Object> row = rows.get(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodesRaw = (List<Map<String, Object>>) row.getOrDefault("nodes", List.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> relsRaw = (List<Map<String, Object>>) row.getOrDefault("rels", List.of());

        List<NodeDTO> nodes = nodesRaw.stream()
                .map(this::toNodeDTO)
                .toList();
        List<RelationshipDTO> rels = relsRaw.stream()
                .map(this::toRelationshipDTO)
                .toList();

        return new SubgraphDTO(nodes, rels);
    }

    private NodeDTO toNodeDTO(Map<String, Object> map) {
        String id = Optional.ofNullable(map.get("id")).map(Object::toString).orElse(null);
        String label = Optional.ofNullable(map.get("label")).map(Object::toString).orElse("Node");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) map.getOrDefault("properties", Map.of());
        return new NodeDTO(id, label, properties);
    }

    private RelationshipDTO toRelationshipDTO(Map<String, Object> map) {
        String id = Optional.ofNullable(map.get("id")).map(Object::toString).orElse(null);
        String type = Optional.ofNullable(map.get("type")).map(Object::toString).orElse("REL");
        String sourceId = Optional.ofNullable(map.get("sourceId")).map(Object::toString).orElse(null);
        String targetId = Optional.ofNullable(map.get("targetId")).map(Object::toString).orElse(null);
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) map.getOrDefault("properties", Map.of());
        return new RelationshipDTO(id, type, sourceId, targetId, properties);
    }

    private String synthesizeAnswer(String question, List<HitDTO> hits, SubgraphDTO subgraph) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a codebase assistant. Use the provided evidence to answer the question.\n");
        sb.append("Question: ").append(question).append("\n\n");
        sb.append("Top hits:\n");
        for (HitDTO hit : hits) {
            Map<String, Object> props = hit.node().properties();
            sb.append("- Score: ").append(hit.score()).append("\n");
            sb.append("  Module: ").append(props.getOrDefault("module", "")).append("\n");
            sb.append("  Owner: ").append(props.getOrDefault("ownerFqcn", "")).append("\n");
            sb.append("  Signature: ").append(props.getOrDefault("ownerSignature", "")).append("\n");
            sb.append("  Path: ").append(props.getOrDefault("path", "")).append(" lines ")
                    .append(props.getOrDefault("startLine", "")).append("-").append(props.getOrDefault("endLine", "")).append("\n");
            sb.append("  Text: ").append(trimText(props.getOrDefault("text", ""), 500)).append("\n");
        }
        sb.append("\nSubgraph summary:\n");
        sb.append("Nodes: ").append(subgraph.nodes().size()).append(", Relationships: ").append(subgraph.relationships().size()).append("\n");

        return chatModel.chat(sb.toString());
    }

    private String trimText(Object textObj, int maxLen) {
        String text = textObj == null ? "" : textObj.toString();
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
