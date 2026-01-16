package com.khalid698.tutorials.codegraph.neo4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class SchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    private final Neo4jClient neo4jClient;
    private final int vectorDimensions;

    public SchemaInitializer(Neo4jClient neo4jClient,
                             @Value("${app.vector-dimensions}") int vectorDimensions) {
        this.neo4jClient = neo4jClient;
        this.vectorDimensions = vectorDimensions;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeSchema() {
        createConstraints();
        createIndexes();
        createVectorIndex();
    }

    private void createConstraints() {
        List<String> constraints = List.of(
                "CREATE CONSTRAINT IF NOT EXISTS FOR (m:Module) REQUIRE m.name IS UNIQUE",
                "CREATE CONSTRAINT IF NOT EXISTS FOR (t:Type) REQUIRE (t.module, t.fqcn) IS UNIQUE",
                "CREATE CONSTRAINT IF NOT EXISTS FOR (m:Method) REQUIRE (m.module, m.fqcn, m.signature) IS UNIQUE",
                "CREATE CONSTRAINT IF NOT EXISTS FOR (e:Endpoint) REQUIRE (e.module, e.httpMethod, e.path) IS UNIQUE",
                "CREATE CONSTRAINT IF NOT EXISTS FOR (c:Chunk) REQUIRE c.id IS UNIQUE"
        );

        constraints.forEach(cypher -> {
            neo4jClient.executeWrite(cypher, Map.of());
            log.info("Ensured constraint: {}", cypher);
        });
    }

    private void createIndexes() {
        List<String> indexes = List.of(
                "CREATE INDEX IF NOT EXISTS FOR (t:Type) ON (t.module)",
                "CREATE INDEX IF NOT EXISTS FOR (m:Method) ON (m.module)",
                "CREATE INDEX IF NOT EXISTS FOR (c:Chunk) ON (c.module)"
        );

        indexes.forEach(cypher -> {
            neo4jClient.executeWrite(cypher, Map.of());
            log.info("Ensured index: {}", cypher);
        });
    }

    private void createVectorIndex() {
        String cypher = """
                CREATE VECTOR INDEX chunk_embedding_idx IF NOT EXISTS
                FOR (c:Chunk) ON (c.embedding)
                OPTIONS {
                  indexConfig: {
                    `vector.dimensions`: $dims,
                    `vector.similarity_function`: 'cosine'
                  }
                }
                """;

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("dims", vectorDimensions);

        neo4jClient.executeWrite(cypher, params);
        log.info("Ensured vector index chunk_embedding_idx with dimensions {}", vectorDimensions);
    }
}
