# Graph Generator Utility

This module parses the codebase with Spoon and upserts a graph representation into Neo4j. Use it to explore modules, classes, methods, and endpoints extracted from the microservices.

## Prerequisites
- JDK 17+
- Maven
- Neo4j running (Docker or local). Defaults: `bolt://localhost:7687`, user `neo4j`, password `password`. Override with env vars `NEO4J_URI`, `NEO4J_USER`, `NEO4J_PASSWORD`.

## Run the extractor
From repo root:
```bash
mvn -pl graph-extractor -am compile exec:java -Dexec.mainClass=pl.piomin.services.graph.GraphExtractorApplication
```

## Neo4j Browser (UI)
- If using the official Neo4j Docker image, open http://localhost:7474 in your browser and sign in with your credentials.
- The Browser lets you run Cypher queries and inspect nodes/relationships visually.

## Cypher snippets to inspect the graph
- Counts by label:
```cypher
MATCH (n) RETURN labels(n) AS labels, count(*) AS cnt ORDER BY cnt DESC;
```
- List some nodes with labels:
```cypher
MATCH (n) RETURN labels(n) AS labels, n LIMIT 25;
```
- Verify module → classes:
```cypher
MATCH (m:Module)-[:CONTAINS]->(c:Class) RETURN m.name, c.fqcn LIMIT 25;
```
- Employee controller endpoints:
```cypher
MATCH (:Class {fqcn:'pl.piomin.services.employee.controller.EmployeeController'})-[:EXPOSES_ENDPOINT]->(e:Endpoint)
RETURN e.httpMethod, e.path;
```
- Total node count:
```cypher
MATCH (n) RETURN count(n) AS total;
```
- Delete all relationships in the database:
```cypher
MATCH ()-[r]-() DELETE r
```
- Delete all data (nodes and relationships):
```cypher
MATCH (n) DETACH DELETE n
```
- Delete index 
```cypher
DROP INDEX chunk_embedding_idx IF EXISTS;
```

## APOC Extended Config
- Refer: https://github.com/neo4j-contrib/neo4j-apoc-procedures/tree/4.4?tab=readme-ov-file
- Verify after starting the neo4j container with the below cypher command:
```cypher
  CALL apoc.help("extended")
```

## CLI option (cypher-shell)
If `cypher-shell` is installed (bundled with Neo4j), you can query via CLI:
```bash
cypher-shell -a bolt://localhost:7687 -u neo4j -p password "MATCH (n) RETURN count(n);"
```

## Tests
Run the in-process Neo4j harness test:
```bash
mvn -pl graph-extractor test
```
