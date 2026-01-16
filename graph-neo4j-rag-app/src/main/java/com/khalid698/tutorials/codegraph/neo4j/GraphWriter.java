package com.khalid698.tutorials.codegraph.neo4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.khalid698.tutorials.codegraph.neo4j.model.ChunkNode;
import com.khalid698.tutorials.codegraph.neo4j.model.ChunkOfMethod;
import com.khalid698.tutorials.codegraph.neo4j.model.EndpointImplementsMethod;
import com.khalid698.tutorials.codegraph.neo4j.model.EndpointNode;
import com.khalid698.tutorials.codegraph.neo4j.model.MethodNode;
import com.khalid698.tutorials.codegraph.neo4j.model.ModuleContainsType;
import com.khalid698.tutorials.codegraph.neo4j.model.ModuleNode;
import com.khalid698.tutorials.codegraph.neo4j.model.TypeDeclaresMethod;
import com.khalid698.tutorials.codegraph.neo4j.model.TypeDependency;
import com.khalid698.tutorials.codegraph.neo4j.model.TypeExposesEndpoint;
import com.khalid698.tutorials.codegraph.neo4j.model.TypeNode;
import com.khalid698.tutorials.codegraph.neo4j.model.UpsertResult;

@Component
public class GraphWriter {

    private static final String UPSERT_MODULES = "cypher/upsertModulesBatch.cypher";
    private static final String UPSERT_TYPES = "cypher/upsertTypesBatch.cypher";
    private static final String UPSERT_METHODS = "cypher/upsertMethodsBatch.cypher";
    private static final String UPSERT_ENDPOINTS = "cypher/upsertEndpointsBatch.cypher";
    private static final String UPSERT_CHUNKS = "cypher/upsertChunksBatch.cypher";
    private static final String REL_MODULE_CONTAINS_TYPES = "cypher/relModuleContainsTypes.cypher";
    private static final String REL_TYPE_DECLARES_METHODS = "cypher/relTypeDeclaresMethods.cypher";
    private static final String REL_TYPE_DEPENDENCIES = "cypher/relTypeDependencies.cypher";
    private static final String REL_TYPE_EXPOSES_ENDPOINTS = "cypher/relTypeExposesEndpoints.cypher";
    private static final String REL_ENDPOINT_IMPLEMENTS_METHODS = "cypher/relEndpointImplementsMethods.cypher";
    private static final String REL_CHUNK_OF_METHODS = "cypher/relChunkOfMethods.cypher";

    private final Neo4jClient neo4jClient;
    private final CypherTemplates templates;

    public GraphWriter(Neo4jClient neo4jClient, CypherTemplates templates) {
        this.neo4jClient = neo4jClient;
        this.templates = templates;
    }

    public UpsertResult upsertModules(List<ModuleNode> modules) {
        return write(UPSERT_MODULES, Map.of("modules", toPayload(modules, this::modulePayload)));
    }

    public UpsertResult upsertTypes(List<TypeNode> types) {
        return write(UPSERT_TYPES, Map.of("types", toPayload(types, this::typePayload)));
    }

    public UpsertResult upsertMethods(List<MethodNode> methods) {
        return write(UPSERT_METHODS, Map.of("methods", toPayload(methods, this::methodPayload)));
    }

    public UpsertResult upsertEndpoints(List<EndpointNode> endpoints) {
        return write(UPSERT_ENDPOINTS, Map.of("endpoints", toPayload(endpoints, this::endpointPayload)));
    }

    public UpsertResult upsertChunks(List<ChunkNode> chunks) {
        return write(UPSERT_CHUNKS, Map.of("chunks", toPayload(chunks, this::chunkPayload)));
    }

    public UpsertResult relateModuleContainsTypes(List<ModuleContainsType> relationships) {
        return write(REL_MODULE_CONTAINS_TYPES,
                Map.of("relationships", toPayload(relationships, this::moduleContainsTypePayload)));
    }

    public UpsertResult relateTypeDeclaresMethods(List<TypeDeclaresMethod> relationships) {
        return write(REL_TYPE_DECLARES_METHODS,
                Map.of("relationships", toPayload(relationships, this::typeDeclaresMethodPayload)));
    }

    public UpsertResult relateTypeDependencies(List<TypeDependency> relationships) {
        return write(REL_TYPE_DEPENDENCIES,
                Map.of("relationships", toPayload(relationships, this::typeDependencyPayload)));
    }

    public UpsertResult relateTypeExposesEndpoints(List<TypeExposesEndpoint> relationships) {
        return write(REL_TYPE_EXPOSES_ENDPOINTS,
                Map.of("relationships", toPayload(relationships, this::typeExposesEndpointPayload)));
    }

    public UpsertResult relateEndpointImplementsMethods(List<EndpointImplementsMethod> relationships) {
        return write(REL_ENDPOINT_IMPLEMENTS_METHODS,
                Map.of("relationships", toPayload(relationships, this::endpointImplementsMethodPayload)));
    }

    public UpsertResult relateChunkOfMethods(List<ChunkOfMethod> relationships) {
        return write(REL_CHUNK_OF_METHODS,
                Map.of("relationships", toPayload(relationships, this::chunkOfMethodPayload)));
    }

    private UpsertResult write(String templatePath, Map<String, Object> params) {
        if (params.values().stream().anyMatch(this::isEmptyList)) {
            return UpsertResult.empty();
        }
        String cypher = templates.load(templatePath);
        return UpsertResult.fromRows(neo4jClient.executeWrite(cypher, params));
    }

    private <T> List<Map<String, Object>> toPayload(List<T> items, java.util.function.Function<T, Map<String, Object>> mapper) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream().map(mapper).toList();
    }

    private boolean isEmptyList(Object candidate) {
        return candidate instanceof List<?> list && list.isEmpty();
    }

    private Map<String, Object> modulePayload(ModuleNode module) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", module.name());
        map.put("path", module.path());
        return map;
    }

    private Map<String, Object> typePayload(TypeNode type) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("module", type.module());
        map.put("fqcn", type.fqcn());
        map.put("name", type.name());
        map.put("kind", type.kind());
        map.put("path", type.path());
        map.put("startLine", type.startLine());
        map.put("endLine", type.endLine());
        return map;
    }

    private Map<String, Object> methodPayload(MethodNode method) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("module", method.module());
        map.put("fqcn", method.fqcn());
        map.put("name", method.name());
        map.put("signature", method.signature());
        map.put("returnType", method.returnType());
        map.put("visibility", method.visibility());
        map.put("static", method.isStatic());
        map.put("abstract", method.isAbstract());
        map.put("path", method.path());
        map.put("startLine", method.startLine());
        map.put("endLine", method.endLine());
        return map;
    }

    private Map<String, Object> endpointPayload(EndpointNode endpoint) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("module", endpoint.module());
        map.put("httpMethod", endpoint.httpMethod());
        map.put("path", endpoint.path());
        return map;
    }

    private Map<String, Object> chunkPayload(ChunkNode chunk) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", chunk.id());
        map.put("module", chunk.module());
        map.put("ownerFqcn", chunk.ownerFqcn());
        map.put("ownerSignature", chunk.ownerSignature());
        map.put("path", chunk.path());
        map.put("startLine", chunk.startLine());
        map.put("endLine", chunk.endLine());
        map.put("kind", chunk.kind());
        map.put("text", chunk.text());
        map.put("textHash", chunk.textHash());
        map.put("embeddingModel", chunk.embeddingModel());
        map.put("embedding", chunk.embedding());
        return map;
    }

    private Map<String, Object> moduleContainsTypePayload(ModuleContainsType rel) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("moduleName", rel.moduleName());
        map.put("typeModule", rel.typeModule());
        map.put("typeFqcn", rel.typeFqcn());
        return map;
    }

    private Map<String, Object> typeDeclaresMethodPayload(TypeDeclaresMethod rel) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("typeModule", rel.typeModule());
        map.put("typeFqcn", rel.typeFqcn());
        map.put("methodModule", rel.methodModule());
        map.put("methodFqcn", rel.methodFqcn());
        map.put("signature", rel.signature());
        return map;
    }

    private Map<String, Object> typeDependencyPayload(TypeDependency rel) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sourceModule", rel.sourceModule());
        map.put("sourceFqcn", rel.sourceFqcn());
        map.put("targetModule", rel.targetModule());
        map.put("targetFqcn", rel.targetFqcn());
        map.put("kind", rel.kind());
        map.put("via", rel.via());
        return map;
    }

    private Map<String, Object> typeExposesEndpointPayload(TypeExposesEndpoint rel) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("typeModule", rel.typeModule());
        map.put("typeFqcn", rel.typeFqcn());
        map.put("endpointModule", rel.endpointModule());
        map.put("httpMethod", rel.httpMethod());
        map.put("path", rel.path());
        return map;
    }

    private Map<String, Object> endpointImplementsMethodPayload(EndpointImplementsMethod rel) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("endpointModule", rel.endpointModule());
        map.put("httpMethod", rel.httpMethod());
        map.put("path", rel.path());
        map.put("methodModule", rel.methodModule());
        map.put("methodFqcn", rel.methodFqcn());
        map.put("signature", rel.signature());
        return map;
    }

    private Map<String, Object> chunkOfMethodPayload(ChunkOfMethod rel) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("chunkId", rel.chunkId());
        map.put("methodModule", rel.methodModule());
        map.put("methodFqcn", rel.methodFqcn());
        map.put("signature", rel.signature());
        return map;
    }
}
