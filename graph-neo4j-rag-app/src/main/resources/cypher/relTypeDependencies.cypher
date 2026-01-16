UNWIND $relationships AS rel
MATCH (source:Type {module: rel.sourceModule, fqcn: rel.sourceFqcn})
MATCH (target:Type {module: rel.targetModule, fqcn: rel.targetFqcn})
MERGE (source)-[r:DEPENDS_ON]->(target)
ON CREATE SET r._created = true,
              r.createdAt = timestamp()
SET r.kind = rel.kind,
    r.via = rel.via,
    r.updatedAt = timestamp()
WITH r, coalesce(r._created, false) AS created
REMOVE r._created
WITH created
RETURN sum(CASE WHEN created THEN 1 ELSE 0 END) AS created,
       sum(CASE WHEN created THEN 0 ELSE 1 END) AS updated
