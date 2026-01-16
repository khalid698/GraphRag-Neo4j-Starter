package com.khalid698.tutorials.codegraph.domain;

import java.util.List;

public record ParsedModule(
        String moduleName,
        String repoPath,
        String sourceRoot,
        List<TypeDef> types,
        List<MethodDef> methods,
        List<EndpointDef> endpoints,
        List<TypeDependency> dependencies
) {
}
