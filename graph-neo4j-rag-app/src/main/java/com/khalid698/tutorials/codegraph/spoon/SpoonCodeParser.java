package com.khalid698.tutorials.codegraph.spoon;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import com.khalid698.tutorials.codegraph.domain.EndpointDef;
import com.khalid698.tutorials.codegraph.domain.MethodDef;
import com.khalid698.tutorials.codegraph.domain.ParsedModule;
import com.khalid698.tutorials.codegraph.domain.TypeDef;
import com.khalid698.tutorials.codegraph.domain.TypeDependency;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtEnum;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtInterface;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtNewArray;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtTypeReference;

public class SpoonCodeParser {

    private static final Logger log = LoggerFactory.getLogger(SpoonCodeParser.class);

    private static final String DEFAULT_SOURCE_ROOT = "src/main/java";

    private final Path repoPath;
    private final String moduleName;
    private final Path sourceRoot;

    public SpoonCodeParser(String repoPath, String moduleName) {
        this(repoPath, moduleName, DEFAULT_SOURCE_ROOT);
    }

    public SpoonCodeParser(String repoPath, String moduleName, String sourceRoot) {
        this.repoPath = Paths.get(repoPath).toAbsolutePath().normalize();
        this.moduleName = moduleName;
        this.sourceRoot = Paths.get(sourceRoot);
    }

    public ParsedModule parse() {
        Path sources = repoPath.resolve(sourceRoot).normalize();
        Launcher launcher = new Launcher();
        launcher.addInputResource(sources.toString());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setComplianceLevel(21);

        CtModel model = launcher.buildModel();
        List<TypeDef> typeDefs = new ArrayList<>();
        List<MethodDef> methodDefs = new ArrayList<>();
        List<EndpointDef> endpointDefs = new ArrayList<>();
        Set<TypeDependency> dependencies = new LinkedHashSet<>();

        for (CtType<?> type : model.getAllTypes()) {
            if (type.isAnonymous() || type.getQualifiedName() == null) {
                continue;
            }

            TypeDef typeDef = toTypeDef(type);
            typeDefs.add(typeDef);

            methodDefs.addAll(extractMethods(type));
            endpointDefs.addAll(extractEndpoints(type));

            collectDependencies(type, dependencies);
        }

        log.info("Parsed module {}: {} types, {} methods, {} endpoints, {} dependencies",
                moduleName, typeDefs.size(), methodDefs.size(), endpointDefs.size(), dependencies.size());

        return new ParsedModule(
                moduleName,
                repoPath.toString(),
                sourceRoot.toString(),
                typeDefs,
                methodDefs,
                endpointDefs,
                new ArrayList<>(dependencies)
        );
    }

    private TypeDef toTypeDef(CtType<?> type) {
        String kind = typeKind(type);
        Position pos = Position.from(type.getPosition(), repoPath);
        return new TypeDef(
                moduleName,
                type.getQualifiedName(),
                type.getSimpleName(),
                kind,
                pos.path(),
                pos.startLine(),
                pos.endLine()
        );
    }

    private List<MethodDef> extractMethods(CtType<?> type) {
        List<MethodDef> defs = new ArrayList<>();
        for (CtMethod<?> method : type.getMethods()) {
            if (!method.getDeclaringType().equals(type)) {
                continue;
            }
            Position pos = Position.from(method.getPosition(), repoPath);
            defs.add(new MethodDef(
                    moduleName,
                    type.getQualifiedName(),
                    method.getSimpleName(),
                    method.getSignature(),
                    safeQualifiedName(method.getType()),
                    visibility(method),
                    method.getModifiers().contains(ModifierKind.STATIC),
                    method.getModifiers().contains(ModifierKind.ABSTRACT),
                    pos.path(),
                    pos.startLine(),
                    pos.endLine()
            ));
        }
        // include constructors (only for classes/enums that support it)
        if (type instanceof CtClass<?> clazz) {
            for (CtConstructor<?> ctor : clazz.getConstructors()) {
                Position pos = Position.from(ctor.getPosition(), repoPath);
                defs.add(new MethodDef(
                        moduleName,
                        type.getQualifiedName(),
                        ctor.getSimpleName(),
                        ctor.getSignature(),
                        null,
                        visibility(ctor),
                        false,
                        false,
                        pos.path(),
                        pos.startLine(),
                        pos.endLine()
                ));
            }
        }
        return defs;
    }

    private List<EndpointDef> extractEndpoints(CtType<?> type) {
        List<EndpointDef> endpoints = new ArrayList<>();

        List<String> classPaths = mappingPaths(type.getAnnotations());
        Set<String> classMethods = mappingHttpMethods(type.getAnnotations());
        if (classPaths.isEmpty()) {
            classPaths = List.of("");
        }

        for (CtMethod<?> method : type.getMethods()) {
            List<CtAnnotation<?>> annotations = method.getAnnotations();
            List<String> methodPaths = mappingPaths(annotations);
            Set<String> httpMethods = mappingHttpMethods(annotations);

            if (methodPaths.isEmpty()) {
                methodPaths = List.of("");
            }
            if (httpMethods.isEmpty()) {
                httpMethods = classMethods.isEmpty() ? Set.of() : classMethods;
            }
            if (httpMethods.isEmpty()) {
                continue; // not an endpoint
            }

            for (String base : classPaths) {
                for (String p : methodPaths) {
                    String fullPath = normalizePath(base, p);
                    for (String httpMethod : httpMethods) {
                        endpoints.add(new EndpointDef(
                                moduleName,
                                httpMethod,
                                fullPath,
                                type.getQualifiedName(),
                                method.getSignature()
                        ));
                    }
                }
            }
        }
        return endpoints;
    }

    private void collectDependencies(CtType<?> type, Set<TypeDependency> dependencies) {
        String sourceFqcn = type.getQualifiedName();
        // imports
        Optional.ofNullable(type.getPosition())
                .flatMap(pos -> Optional.ofNullable(pos.getCompilationUnit()))
                .map(unit -> unit.getImports().stream()
                        .map(CtImport::getReference)
                        .filter(Objects::nonNull)
                        .toList())
                .ifPresent(imports -> imports.forEach(ref ->
                        addDependency(dependencies, sourceFqcn, safeQualifiedName(ref), "type-usage", "import")));

        // fields
        type.getFields().forEach(field -> addDependency(
                dependencies, sourceFqcn, safeQualifiedName(field.getType()), "type-usage", "field"));

        // methods params / return / annotations
        type.getMethods().forEach(method -> {
            method.getParameters().forEach(param -> addDependency(
                    dependencies, sourceFqcn, safeQualifiedName(param.getType()), "type-usage", "parameter"));
            addDependency(dependencies, sourceFqcn, safeQualifiedName(method.getType()), "type-usage", "return");
            addAnnotationDeps(dependencies, sourceFqcn, method.getAnnotations());
        });

        // constructors (where available)
        if (type instanceof CtClass<?> clazz) {
            clazz.getConstructors().forEach(ctor -> {
                ctor.getParameters().forEach(param -> addDependency(
                        dependencies, sourceFqcn, safeQualifiedName(param.getType()), "type-usage", "parameter"));
                addAnnotationDeps(dependencies, sourceFqcn, ctor.getAnnotations());
            });
        }

        // type-level annotations
        addAnnotationDeps(dependencies, sourceFqcn, type.getAnnotations());
    }

    private void addAnnotationDeps(Set<TypeDependency> dependencies, String sourceFqcn, List<CtAnnotation<?>> annotations) {
        annotations.stream()
                .filter(Objects::nonNull)
                .forEach(ann -> addDependency(
                        dependencies, sourceFqcn, safeQualifiedName(ann.getAnnotationType()), "type-usage", "annotation"));
    }

    private void addDependency(Set<TypeDependency> dependencies, String sourceFqcn, String targetFqcn, String kind, String via) {
        if (!StringUtils.hasText(targetFqcn)) {
            return;
        }
        if (targetFqcn.startsWith("java.lang")) {
            return;
        }
        if (sourceFqcn.equals(targetFqcn)) {
            return;
        }
        dependencies.add(new TypeDependency(sourceFqcn, targetFqcn, kind, via));
    }

    private List<String> mappingPaths(List<CtAnnotation<?>> annotations) {
        List<String> paths = new ArrayList<>();
        annotations.forEach(ann -> readPathsFromAnnotation(ann, paths::addAll));
        return paths;
    }

    private Set<String> mappingHttpMethods(List<CtAnnotation<?>> annotations) {
        Set<String> methods = new HashSet<>();
        annotations.forEach(ann -> readHttpMethodsFromAnnotation(ann, methods::addAll));
        return methods;
    }

    private void readPathsFromAnnotation(CtAnnotation<?> annotation, Consumer<List<String>> consumer) {
        String qn = annotation.getAnnotationType().getQualifiedName();
        if (!isRequestMappingAnnotation(qn)) {
            return;
        }
        List<String> collected = new ArrayList<>();
        collected.addAll(extractStringValues(annotation, "value"));
        collected.addAll(extractStringValues(annotation, "path"));
        consumer.accept(collected);
    }

    private void readHttpMethodsFromAnnotation(CtAnnotation<?> annotation, Consumer<Set<String>> consumer) {
        String qn = annotation.getAnnotationType().getQualifiedName();
        Set<String> methods = new HashSet<>();
        switch (qn) {
            case "org.springframework.web.bind.annotation.GetMapping" -> methods.add("GET");
            case "org.springframework.web.bind.annotation.PostMapping" -> methods.add("POST");
            case "org.springframework.web.bind.annotation.PutMapping" -> methods.add("PUT");
            case "org.springframework.web.bind.annotation.DeleteMapping" -> methods.add("DELETE");
            case "org.springframework.web.bind.annotation.PatchMapping" -> methods.add("PATCH");
            case "org.springframework.web.bind.annotation.RequestMapping" -> {
                List<String> methodValues = extractEnumValues(annotation, "method");
                methods.addAll(methodValues);
            }
            default -> {
            }
        }
        consumer.accept(methods);
    }

    private boolean isRequestMappingAnnotation(String qualifiedName) {
        return qualifiedName != null && (
                qualifiedName.equals("org.springframework.web.bind.annotation.RequestMapping") ||
                        qualifiedName.equals("org.springframework.web.bind.annotation.GetMapping") ||
                        qualifiedName.equals("org.springframework.web.bind.annotation.PostMapping") ||
                        qualifiedName.equals("org.springframework.web.bind.annotation.PutMapping") ||
                        qualifiedName.equals("org.springframework.web.bind.annotation.DeleteMapping") ||
                        qualifiedName.equals("org.springframework.web.bind.annotation.PatchMapping"));
    }

    private List<String> extractStringValues(CtAnnotation<?> annotation, String attribute) {
        Object raw = annotation.getValue(attribute);
        if (raw == null) {
            return List.of();
        }
        return extractStrings(raw);
    }

    private List<String> extractEnumValues(CtAnnotation<?> annotation, String attribute) {
        Object raw = annotation.getValue(attribute);
        if (raw == null) {
            return List.of();
        }
        return extractStrings(raw).stream()
                .map(val -> {
                    int idx = val.lastIndexOf('.');
                    return idx >= 0 ? val.substring(idx + 1) : val;
                })
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private List<String> extractStrings(Object raw) {
        if (raw instanceof CtLiteral<?> literal) {
            Object value = literal.getValue();
            return value == null ? List.of() : List.of(value.toString());
        }
        if (raw instanceof CtNewArray<?> array) {
            return array.getElements().stream()
                    .flatMap(elem -> extractStrings(elem).stream())
                    .toList();
        }
        if (raw instanceof List<?> list) {
            return list.stream().flatMap(elem -> extractStrings(elem).stream()).toList();
        }
        if (raw instanceof CtExpression<?> expr) {
            return List.of(expr.toString().replace("\"", ""));
        }
        return raw == null ? List.of() : List.of(raw.toString());
    }

    private String typeKind(CtType<?> type) {
        if (type instanceof CtClass) {
            return "class";
        }
        if (type instanceof CtInterface) {
            return "interface";
        }
        if (type instanceof CtEnum) {
            return "enum";
        }
        return "type";
    }

    private String visibility(CtMethod<?> method) {
        return method.getVisibility() != null ? method.getVisibility().toString().toLowerCase() : "package-private";
    }

    private String visibility(CtConstructor<?> ctor) {
        return ctor.getVisibility() != null ? ctor.getVisibility().toString().toLowerCase() : "package-private";
    }

    private String normalizePath(String base, String methodPath) {
        String b = StringUtils.hasText(base) ? base : "";
        String m = StringUtils.hasText(methodPath) ? methodPath : "";
        String combined = ("/" + b + "/" + m).replaceAll("//+", "/");
        if (combined.length() > 1 && combined.endsWith("/")) {
            combined = combined.substring(0, combined.length() - 1);
        }
        return combined.isEmpty() ? "/" : combined;
    }

    private String safeQualifiedName(Object ref) {
        try {
            if (ref instanceof CtTypeReference<?> typeRef) {
                return typeRef.getQualifiedName();
            }
            if (ref instanceof CtPackageReference pkgRef) {
                return pkgRef.getQualifiedName();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
/*
    private String safeQualifiedName(CtReference ref) {
        try {
            return ref == null ? null : ref.getQualifiedName();
        } catch (Exception e) {
            return null;
        }
    }
*/    
    private record Position(String path, Integer startLine, Integer endLine) {
        static Position from(spoon.reflect.cu.SourcePosition pos, Path repoRoot) {
            if (pos == null || !pos.isValidPosition() || pos.getFile() == null) {
                return new Position(null, null, null);
            }
            Path relative = repoRoot.relativize(pos.getFile().toPath().toAbsolutePath().normalize());
            return new Position(relative.toString(), pos.getLine(), pos.getEndLine());
        }
    }
}
