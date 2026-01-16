package com.khalid698.tutorials.codegraph.domain;

public record MethodDef(
        String moduleName,
        String declaringTypeFqcn,
        String name,
        String signature,
        String returnType,
        String visibility,
        boolean isStatic,
        boolean isAbstract,
        String path,
        Integer startLine,
        Integer endLine
) {
}
