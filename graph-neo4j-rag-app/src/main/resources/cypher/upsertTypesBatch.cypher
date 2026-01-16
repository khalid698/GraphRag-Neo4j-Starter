UNWIND $types AS type
MERGE (t:Type {module: type.module, fqcn: type.fqcn})
ON CREATE SET t._created = true,
              t.createdAt = timestamp()
SET t.name = type.name,
    t.kind = type.kind,
    t.path = type.path,
    t.startLine = type.startLine,
    t.endLine = type.endLine,
    t.updatedAt = timestamp()
WITH t, coalesce(t._created, false) AS created
REMOVE t._created
WITH created
RETURN sum(CASE WHEN created THEN 1 ELSE 0 END) AS created,
       sum(CASE WHEN created THEN 0 ELSE 1 END) AS updated
