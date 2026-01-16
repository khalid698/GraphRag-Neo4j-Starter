package com.khalid698.tutorials.codegraph.neo4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionCallback;
import org.springframework.stereotype.Component;

@Component
public class Neo4jClient {

    private final Driver driver;

    public Neo4jClient(Driver driver) {
        this.driver = driver;
    }

    public List<Map<String, Object>> executeRead(String cypher, Map<String, Object> params) {
        return run(cypher, params, TransactionMode.READ);
    }

    public List<Map<String, Object>> executeWrite(String cypher, Map<String, Object> params) {
        return run(cypher, params, TransactionMode.WRITE);
    }

    private List<Map<String, Object>> run(String cypher, Map<String, Object> params, TransactionMode mode) {
        Objects.requireNonNull(cypher, "cypher must not be null");
        try (Session session = driver.session()) {
            TransactionCallback<List<Map<String, Object>>> work =
                    tx -> tx.run(cypher, toParams(params)).list(Record::asMap);
            return switch (mode) {
                case READ -> session.executeRead(work);
                case WRITE -> session.executeWrite(work);
            };
        }
    }

    private Map<String, Object> toParams(Map<String, Object> params) {
        return params == null ? Map.of() : params;
    }

    private enum TransactionMode {
        READ, WRITE
    }
}
