package com.khalid698.tutorials.codegraph.ingest;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.khalid698.tutorials.codegraph.domain.MethodDef;

@Component
public class EmbeddingTextBuilder {

    private static final int MAX_SNIPPET_CHARS = 4000;

    public String build(MethodDef method, List<String> annotations, String codeSnippet) {
        StringBuilder sb = new StringBuilder();

        sb.append("Module: ").append(nullToEmpty(method.moduleName())).append("\n");
        sb.append("Type: ").append(nullToEmpty(method.declaringTypeFqcn())).append("\n");
        sb.append("Signature: ").append(nullToEmpty(method.signature())).append("\n");
        sb.append("Return: ").append(nullToEmpty(method.returnType())).append("\n");
        sb.append("Visibility: ").append(nullToEmpty(method.visibility())).append("\n");
        if (method.isStatic()) {
            sb.append("Modifiers: static ");
        }
        if (method.isAbstract()) {
            sb.append("abstract ");
        }
        if (method.isStatic() || method.isAbstract()) {
            sb.append("\n");
        }

        if (annotations != null && !annotations.isEmpty()) {
            sb.append("Annotations:\n");
            annotations.forEach(a -> sb.append("- ").append(a).append("\n"));
        }

        if (StringUtils.hasText(method.path())) {
            sb.append("Source: ").append(method.path());
            if (method.startLine() != null && method.endLine() != null) {
                sb.append(" [lines ").append(method.startLine()).append("-").append(method.endLine()).append("]");
            }
            sb.append("\n");
        }

        sb.append("Code:\n");
        String boundedSnippet = boundSnippet(codeSnippet);
        sb.append(nullToEmpty(boundedSnippet)).append("\n");

        return sb.toString().trim();
    }

    private String boundSnippet(String snippet) {
        if (!StringUtils.hasText(snippet)) {
            return "";
        }
        if (snippet.length() <= MAX_SNIPPET_CHARS) {
            return snippet;
        }
        return snippet.substring(0, MAX_SNIPPET_CHARS);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
