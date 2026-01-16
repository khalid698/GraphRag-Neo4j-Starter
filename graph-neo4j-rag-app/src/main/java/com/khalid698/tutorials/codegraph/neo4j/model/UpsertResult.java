package com.khalid698.tutorials.codegraph.neo4j.model;

import java.util.List;
import java.util.Map;

public record UpsertResult(long created, long updated) {

    public static UpsertResult empty() {
        return new UpsertResult(0, 0);
    }

    public static UpsertResult fromRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return empty();
        }
        Map<String, Object> row = rows.get(0);
        return new UpsertResult(asLong(row.get("created")), asLong(row.get("updated")));
    }

    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
