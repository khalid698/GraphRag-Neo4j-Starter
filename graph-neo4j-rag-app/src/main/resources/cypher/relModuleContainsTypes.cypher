UNWIND $relationships AS rel
MATCH (m:Module {name: rel.moduleName})
MATCH (t:Type {module: rel.typeModule, fqcn: rel.typeFqcn})
MERGE (m)-[r:CONTAINS]->(t)
ON CREATE SET r._created = true,
              r.createdAt = timestamp()
SET r.updatedAt = timestamp()
WITH r, coalesce(r._created, false) AS created
REMOVE r._created
WITH created
RETURN sum(CASE WHEN created THEN 1 ELSE 0 END) AS created,
       sum(CASE WHEN created THEN 0 ELSE 1 END) AS updated
