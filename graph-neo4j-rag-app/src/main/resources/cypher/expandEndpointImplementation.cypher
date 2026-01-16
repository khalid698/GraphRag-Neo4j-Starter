MATCH (e:Endpoint {module: $module, httpMethod: $httpMethod, path: $path})-[:HANDLES]->(m:Method)-[:BELONGS_TO]->(t:Type)
RETURN e.path AS path,
       e.httpMethod AS httpMethod,
       m.signature AS signature,
       m.fqcn AS fqcn,
       t.fqcn AS typeFqcn,
       t.name AS typeName
