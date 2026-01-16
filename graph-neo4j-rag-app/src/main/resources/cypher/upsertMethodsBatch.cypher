UNWIND $methods AS method
MERGE (m:Method {module: method.module, fqcn: method.fqcn, signature: method.signature})
ON CREATE SET m._created = true,
              m.createdAt = timestamp()
SET m.name = method.name,
    m.returnType = method.returnType,
    m.visibility = method.visibility,
    m.static = method.static,
    m.abstract = method.abstract,
    m.path = method.path,
    m.startLine = method.startLine,
    m.endLine = method.endLine,
    m.updatedAt = timestamp()
WITH m, coalesce(m._created, false) AS created
REMOVE m._created
WITH created
RETURN sum(CASE WHEN created THEN 1 ELSE 0 END) AS created,
       sum(CASE WHEN created THEN 0 ELSE 1 END) AS updated
