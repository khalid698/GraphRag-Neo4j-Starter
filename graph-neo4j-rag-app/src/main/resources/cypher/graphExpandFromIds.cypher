UNWIND $ids AS nodeId
MATCH (n) WHERE toString(id(n)) = nodeId
WITH collect(DISTINCT n) AS seeds
UNWIND seeds AS s
OPTIONAL MATCH (s)-[r*1..$hops]-(t)
WITH seeds + collect(DISTINCT t) AS nodes
UNWIND nodes AS n
OPTIONAL MATCH (n)-[rel]-(n2)
WHERE n2 IN nodes
WITH collect(DISTINCT n) AS nodes, collect(DISTINCT rel) AS rels
RETURN [n IN nodes | {id: toString(id(n)), label: head(labels(n)), properties: properties(n)}] AS nodes,
       [r IN rels | {id: toString(id(r)), type: type(r), sourceId: toString(id(startNode(r))), targetId: toString(id(endNode(r))), properties: properties(r)}] AS rels
