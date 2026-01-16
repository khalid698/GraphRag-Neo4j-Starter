UNWIND $relationships AS rel
MATCH (t:Type {module: rel.typeModule, fqcn: rel.typeFqcn})
MATCH (m:Method {module: rel.methodModule, fqcn: rel.methodFqcn, signature: rel.signature})
MERGE (t)-[r:DECLARES]->(m)
ON CREATE SET r._created = true,
              r.createdAt = timestamp()
SET r.updatedAt = timestamp()
WITH r, coalesce(r._created, false) AS created
REMOVE r._created
WITH created
RETURN sum(CASE WHEN created THEN 1 ELSE 0 END) AS created,
       sum(CASE WHEN created THEN 0 ELSE 1 END) AS updated
