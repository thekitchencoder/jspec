package uk.codery.jspec.model;

import java.util.Map;
import java.util.Optional;

/**
 * Utility for recognising and constructing {@link ContextPathReference} instances
 * from raw operand shapes (typically produced by Jackson YAML/JSON parsing).
 */
public final class ContextPathReferences {

    private ContextPathReferences() {}

    /**
     * Returns a {@link ContextPathReference} if {@code operand} is exactly the
     * shape {@code { "$contextPath": "<non-blank-string>" }}, otherwise empty.
     *
     * <p>The sentinel must be the sole key — extra keys cause the operand to be
     * treated as a plain map, leaving room to add typed coercion hints
     * (e.g. {@code "as"}) in a future PR without silently changing behaviour.
     */
    public static Optional<ContextPathReference> fromOperand(Object operand) {
        if (!(operand instanceof Map<?, ?> map)) return Optional.empty();
        if (map.size() != 1) return Optional.empty();
        Object value = map.get(ContextPathReference.SENTINEL_KEY);
        if (!(value instanceof String path) || path.isBlank()) return Optional.empty();
        return Optional.of(new ContextPathReference(path));
    }
}
