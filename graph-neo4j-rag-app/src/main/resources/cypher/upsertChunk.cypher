MERGE (c:Chunk {id: $id})
SET c.module = $module,
    c.content = $content,
    c.embedding = $embedding,
    c.summary = $summary,
    c.updatedAt = timestamp()
RETURN c
