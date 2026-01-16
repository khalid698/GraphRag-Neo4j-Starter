MATCH (t:Type {module: $module, fqcn: $fqcn})-[:DEPENDS_ON*1..2]->(dep:Type)
RETURN dep.module AS module,
       dep.fqcn AS fqcn,
       dep.name AS name,
       dep.kind AS kind
