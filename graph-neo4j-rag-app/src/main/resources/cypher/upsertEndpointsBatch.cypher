UNWIND $endpoints AS endpoint
MERGE (e:Endpoint {module: endpoint.module, httpMethod: endpoint.httpMethod, path: endpoint.path})
ON CREATE SET e._created = true,
              e.createdAt = timestamp()
SET e.updatedAt = timestamp()
WITH e, coalesce(e._created, false) AS created
REMOVE e._created
WITH created
RETURN sum(CASE WHEN created THEN 1 ELSE 0 END) AS created,
       sum(CASE WHEN created THEN 0 ELSE 1 END) AS updated
