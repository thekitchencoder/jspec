package uk.codery.jspec.model;

/**
 * A late-bound reference into a context document.
 *
 * <p>When a {@code Specification} is evaluated with the two-arg
 * {@code evaluate(targetDoc, contextDoc)} form, any operand whose runtime
 * value is a {@code ContextPathReference} is replaced — before the operator
 * handler is invoked — with the value found by walking {@code path} into the
 * context document. If the path does not resolve, the containing criterion
 * is marked {@code UNDETERMINED}.
 *
 * <p>Created by the spec normalisation walk from raw map literals shaped
 * {@code { "$contextPath": "<path>" }} (see {@link Specification}).
 *
 * @param path dot-notation path into the context document; must be non-blank
 */
public record ContextPathReference(String path) {

    public static final String SENTINEL_KEY = "$contextPath";

    public ContextPathReference {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("ContextPathReference path must be non-blank");
        }
    }
}
