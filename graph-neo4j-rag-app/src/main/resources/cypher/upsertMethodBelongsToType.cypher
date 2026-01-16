MATCH (t:Type {module: $typeModule, fqcn: $typeFqcn})
MATCH (m:Method {module: $methodModule, fqcn: $methodFqcn, signature: $signature})
MERGE (m)-[r:BELONGS_TO]->(t)
SET r.updatedAt = timestamp()
RETURN m, r, t
