UNWIND $modules AS module
MERGE (m:Module {name: module.name})
ON CREATE SET m.path = module.path,
              m.createdAt = timestamp(),
              m._created = true
SET m.path = module.path,
    m.updatedAt = timestamp()
WITH m, coalesce(m._created, false) AS created
REMOVE m._created
WITH created
RETURN sum(CASE WHEN created THEN 1 ELSE 0 END) AS created,
       sum(CASE WHEN created THEN 0 ELSE 1 END) AS updated
