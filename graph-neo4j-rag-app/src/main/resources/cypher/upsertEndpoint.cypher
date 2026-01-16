MERGE (e:Endpoint {module: $module, httpMethod: $httpMethod, path: $path})
SET e.name = $name,
    e.summary = $summary,
    e.updatedAt = timestamp()
RETURN e
