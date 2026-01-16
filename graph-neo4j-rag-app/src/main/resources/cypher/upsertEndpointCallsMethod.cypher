MATCH (e:Endpoint {module: $endpointModule, httpMethod: $httpMethod, path: $path})
MATCH (m:Method {module: $methodModule, fqcn: $methodFqcn, signature: $signature})
MERGE (e)-[r:HANDLES]->(m)
SET r.updatedAt = timestamp()
RETURN e, r, m
