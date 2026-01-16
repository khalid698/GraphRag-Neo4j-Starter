MERGE (m:Method {module: $module, fqcn: $fqcn, signature: $signature})
SET m.name = $name,
    m.visibility = $visibility,
    m.httpMethod = $httpMethod,
    m.updatedAt = timestamp()
RETURN m
