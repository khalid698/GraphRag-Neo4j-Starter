MATCH (source:Type {fqcn: $sourceFqcn})
MATCH (target:Type {fqcn: $targetFqcn})
MATCH p = shortestPath((source)-[:DEPENDS_ON*..5]->(target))
RETURN p
