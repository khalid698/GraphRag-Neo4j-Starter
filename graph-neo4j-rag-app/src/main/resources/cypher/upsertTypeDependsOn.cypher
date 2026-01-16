MATCH (source:Type {module: $sourceModule, fqcn: $sourceFqcn})
MATCH (target:Type {module: $targetModule, fqcn: $targetFqcn})
MERGE (source)-[r:DEPENDS_ON]->(target)
SET r.reason = $reason,
    r.updatedAt = timestamp()
RETURN source, r, target
