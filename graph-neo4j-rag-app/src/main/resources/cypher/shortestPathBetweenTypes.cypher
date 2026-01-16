MATCH (source:Type {module: $sourceModule, fqcn: $sourceFqcn})
MATCH (target:Type {module: $targetModule, fqcn: $targetFqcn})
MATCH p = shortestPath((source)-[:DEPENDS_ON*..5]->(target))
RETURN p
