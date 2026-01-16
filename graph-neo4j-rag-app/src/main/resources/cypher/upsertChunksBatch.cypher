UNWIND $chunks AS chunk
MERGE (c:Chunk {id: chunk.id})
ON CREATE SET c._created = true,
              c.createdAt = timestamp()
SET c.module = chunk.module,
    c.ownerFqcn = chunk.ownerFqcn,
    c.ownerSignature = chunk.ownerSignature,
    c.path = chunk.path,
    c.startLine = chunk.startLine,
    c.endLine = chunk.endLine,
    c.kind = chunk.kind,
    c.text = chunk.text,
    c.textHash = chunk.textHash,
    c.embeddingModel = chunk.embeddingModel,
    c.embedding = chunk.embedding,
    c.updatedAt = timestamp()
WITH c, coalesce(c._created, false) AS created
REMOVE c._created
WITH created
RETURN sum(CASE WHEN created THEN 1 ELSE 0 END) AS created,
       sum(CASE WHEN created THEN 0 ELSE 1 END) AS updated
