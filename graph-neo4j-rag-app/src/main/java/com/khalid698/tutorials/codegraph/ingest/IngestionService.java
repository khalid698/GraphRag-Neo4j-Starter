package com.khalid698.tutorials.codegraph.ingest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.khalid698.tutorials.codegraph.ai.EmbeddingService;
import com.khalid698.tutorials.codegraph.domain.ChunkDoc;
import com.khalid698.tutorials.codegraph.domain.EndpointDef;
import com.khalid698.tutorials.codegraph.domain.MethodDef;
import com.khalid698.tutorials.codegraph.domain.ParsedModule;
import com.khalid698.tutorials.codegraph.domain.TypeDef;
import com.khalid698.tutorials.codegraph.domain.TypeDependency;
import com.khalid698.tutorials.codegraph.neo4j.GraphWriter;
import com.khalid698.tutorials.codegraph.neo4j.model.ChunkNode;
import com.khalid698.tutorials.codegraph.neo4j.model.EndpointImplementsMethod;
import com.khalid698.tutorials.codegraph.neo4j.model.EndpointNode;
import com.khalid698.tutorials.codegraph.neo4j.model.MethodNode;
import com.khalid698.tutorials.codegraph.neo4j.model.ModuleContainsType;
import com.khalid698.tutorials.codegraph.neo4j.model.ModuleNode;
import com.khalid698.tutorials.codegraph.neo4j.model.TypeDeclaresMethod;
import com.khalid698.tutorials.codegraph.neo4j.model.TypeExposesEndpoint;
import com.khalid698.tutorials.codegraph.neo4j.model.TypeNode;
import com.khalid698.tutorials.codegraph.neo4j.model.UpsertResult;
import com.khalid698.tutorials.codegraph.spoon.SpoonCodeParser;

@Service
public class IngestionService {

    private final GraphWriter graphWriter;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;

    public IngestionService(GraphWriter graphWriter,
                            ChunkingService chunkingService,
                            EmbeddingService embeddingService) {
        this.graphWriter = graphWriter;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
    }

    public Summary ingest(String repoPath, String moduleName, boolean includeTests, Integer chunkChars, Integer overlap, boolean embed) {
        long start = System.currentTimeMillis();

        SpoonCodeParser parser = new SpoonCodeParser(repoPath, moduleName, includeTests ? "src" : "src/main/java");
        ParsedModule parsed = parser.parse();

        UpsertResult modResult = graphWriter.upsertModules(List.of(new ModuleNode(moduleName, repoPath)));
        UpsertResult typeResult = graphWriter.upsertTypes(mapTypes(parsed.types()));
        UpsertResult methodResult = graphWriter.upsertMethods(mapMethods(parsed.methods()));
        UpsertResult endpointResult = graphWriter.upsertEndpoints(mapEndpoints(parsed.endpoints()));

        graphWriter.relateModuleContainsTypes(mapModuleContains(parsed.types(), moduleName));
        graphWriter.relateTypeDeclaresMethods(mapTypeDeclares(parsed.methods()));
        graphWriter.relateTypeDependencies(mapTypeDependencies(parsed.dependencies(), moduleName));
        graphWriter.relateTypeExposesEndpoints(mapTypeExposes(parsed.endpoints(), parsed.types()));
        graphWriter.relateEndpointImplementsMethods(mapEndpointImplements(parsed.endpoints()));

        List<ChunkDoc> chunkDocs = buildChunks(repoPath, parsed.methods(), chunkChars, overlap);
        UpsertResult chunkResult = embed
                ? embeddingService.embedAndPersistChunks(chunkDocs)
                : graphWriter.upsertChunks(mapChunkNodes(chunkDocs));

        long duration = System.currentTimeMillis() - start;
        int relationships = parsed.dependencies().size()
                + parsed.methods().size()
                + parsed.types().size()
                + parsed.endpoints().size();

        return new Summary(moduleName,
                parsed.types().size(),
                parsed.methods().size(),
                parsed.endpoints().size(),
                chunkDocs.size(),
                relationships,
                duration);
    }

    private List<TypeNode> mapTypes(List<TypeDef> defs) {
        if (defs == null) return List.of();
        return defs.stream().map(d -> new TypeNode(
                d.moduleName(),
                d.fqcn(),
                d.name(),
                d.kind(),
                d.path(),
                d.startLine(),
                d.endLine()
        )).toList();
    }

    private List<MethodNode> mapMethods(List<MethodDef> defs) {
        if (defs == null) return List.of();
        return defs.stream().map(m -> new MethodNode(
                m.moduleName(),
                m.declaringTypeFqcn(),
                m.name(),
                m.signature(),
                m.returnType(),
                m.visibility(),
                m.isStatic(),
                m.isAbstract(),
                m.path(),
                m.startLine(),
                m.endLine()
        )).toList();
    }

    private List<EndpointNode> mapEndpoints(List<EndpointDef> defs) {
        if (defs == null) return List.of();
        return defs.stream().map(e -> new EndpointNode(
                e.moduleName(),
                e.httpMethod(),
                e.path()
        )).toList();
    }

    private List<ModuleContainsType> mapModuleContains(List<TypeDef> types, String moduleName) {
        if (types == null) return List.of();
        return types.stream()
                .map(t -> new ModuleContainsType(moduleName, t.moduleName(), t.fqcn()))
                .toList();
    }

    private List<TypeDeclaresMethod> mapTypeDeclares(List<MethodDef> methods) {
        if (methods == null) return List.of();
        return methods.stream()
                .map(m -> new TypeDeclaresMethod(m.moduleName(), m.declaringTypeFqcn(), m.moduleName(), m.declaringTypeFqcn(), m.signature()))
                .toList();
    }

    private List<com.khalid698.tutorials.codegraph.neo4j.model.TypeDependency> mapTypeDependencies(List<TypeDependency> deps, String moduleName) {
        if (deps == null) return List.of();
        return deps.stream()
                .map(d -> new com.khalid698.tutorials.codegraph.neo4j.model.TypeDependency(moduleName, d.sourceFqcn(), moduleName, d.targetFqcn(), d.kind(), d.via()))
                .toList();
    }

    private List<TypeExposesEndpoint> mapTypeExposes(List<EndpointDef> endpoints, List<TypeDef> types) {
        if (endpoints == null) return List.of();
        return endpoints.stream()
                .map(e -> new TypeExposesEndpoint(e.moduleName(), e.implementingTypeFqcn(), e.moduleName(), e.httpMethod(), e.path()))
                .toList();
    }

    private List<EndpointImplementsMethod> mapEndpointImplements(List<EndpointDef> endpoints) {
        if (endpoints == null) return List.of();
        return endpoints.stream()
                .map(e -> new EndpointImplementsMethod(e.moduleName(), e.httpMethod(), e.path(), e.moduleName(), e.implementingTypeFqcn(), e.implementingSignature()))
                .toList();
    }

    private List<ChunkDoc> buildChunks(String repoPath, List<MethodDef> methods, Integer chunkChars, Integer overlap) {
        if (CollectionUtils.isEmpty(methods)) {
            return List.of();
        }
        List<ChunkDoc> chunks = new ArrayList<>();
        for (MethodDef method : methods) {
            String snippet = readSnippet(repoPath, method.path(), method.startLine(), method.endLine());
            chunkingService.chunkMethod(method, List.of(), snippet, chunkChars, overlap)
                    .forEach(c -> chunks.add(new ChunkDoc(
                            c.id(),
                            c.moduleName(),
                            c.ownerFqcn(),
                            c.ownerSignature(),
                            c.path(),
                            c.startLine(),
                            c.endLine(),
                            c.kind(),
                            c.text(),
                            c.textHash()
                    )));
        }
        return chunks;
    }

    private List<ChunkNode> mapChunkNodes(List<ChunkDoc> docs) {
        return docs.stream().map(d -> new ChunkNode(
                d.id(),
                d.moduleName(),
                d.ownerFqcn(),
                d.ownerSignature(),
                d.path(),
                d.startLine(),
                d.endLine(),
                d.kind(),
                d.text(),
                d.textHash(),
                null,
                null
        )).toList();
    }

    private String readSnippet(String repoPath, String relativePath, Integer startLine, Integer endLine) {
        if (!StringUtils.hasText(relativePath) || startLine == null || endLine == null) {
            return "";
        }
        Path file = Path.of(repoPath).resolve(relativePath);
        if (!Files.exists(file)) {
            return "";
        }
        try {
            List<String> lines = Files.readAllLines(file);
            int from = Math.max(1, startLine);
            int to = Math.min(lines.size(), endLine);
            StringBuilder sb = new StringBuilder();
            for (int i = from; i <= to; i++) {
                sb.append(lines.get(i - 1)).append("\n");
            }
            return sb.toString();
        } catch (IOException e) {
            return "";
        }
    }

    public record Summary(String moduleName, int types, int methods, int endpoints, int chunks, int relationships, long durationMs) {
    }
}
