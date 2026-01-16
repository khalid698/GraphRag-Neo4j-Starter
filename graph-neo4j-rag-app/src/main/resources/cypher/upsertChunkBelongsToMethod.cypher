MATCH (c:Chunk {id: $chunkId})
MATCH (m:Method {module: $methodModule, fqcn: $methodFqcn, signature: $signature})
MERGE (c)-[r:FROM_METHOD]->(m)
SET r.updatedAt = timestamp()
RETURN c, r, m
