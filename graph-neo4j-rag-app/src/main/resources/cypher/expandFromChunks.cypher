UNWIND $chunkIds AS chunkId
MATCH (c:Chunk {id: chunkId})-[:CHUNK_OF]->(m:Method)<-[:DECLARES]-(t:Type)
WITH collect(DISTINCT c) AS chunks, collect(DISTINCT m) AS methods, collect(DISTINCT t) AS types

UNWIND types AS t
OPTIONAL MATCH (t)-[:DEPENDS_ON]->(dep:Type)
WITH chunks, methods, collect(DISTINCT t) + collect(DISTINCT dep) AS typeNodes

UNWIND typeNodes AS t
OPTIONAL MATCH (t)-[:EXPOSES_ENDPOINT]->(e:Endpoint)
WITH chunks, methods, collect(DISTINCT t) AS typeNodes, collect(DISTINCT e) AS endpoints

OPTIONAL MATCH (e:Endpoint)-[:IMPLEMENTS]->(impl:Method)
WITH chunks, methods + collect(DISTINCT impl) AS methodsAll, typeNodes, endpoints

WITH chunks + methodsAll + typeNodes + endpoints AS nodes
UNWIND nodes AS n
OPTIONAL MATCH (n)-[r]->(n2)
WHERE n2 IN nodes
WITH collect(DISTINCT n) AS nodes, collect(DISTINCT r) AS rels
RETURN [n IN nodes | {id: toString(id(n)), label: head(labels(n)), properties: properties(n)}] AS nodes,
       [r IN rels | {id: toString(id(r)), type: type(r), sourceId: toString(id(startNode(r))), targetId: toString(id(endNode(r))), properties: properties(r)}] AS rels
