MERGE (t:Type {module: $module, fqcn: $fqcn})
SET t.name = $name,
    t.kind = $kind,
    t.updatedAt = timestamp()
RETURN t
