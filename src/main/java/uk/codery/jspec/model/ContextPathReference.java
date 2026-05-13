package uk.codery.jspec.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;

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
 * <p>Jackson serialises this record as the sentinel map {@code { "$contextPath": "<path>" }}
 * via {@link #toJson()} and reconstructs it via {@link #fromJson(Map)} so that
 * formatters and round-trips preserve the on-disk shape.
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

    /**
     * Serialises this reference as its sentinel map shape for Jackson.
     *
     * @return a single-entry map of {@code { "$contextPath": path }}
     */
    @JsonValue
    public Map<String, String> toJson() {
        return Map.of(SENTINEL_KEY, path);
    }

    /**
     * Reconstructs a {@code ContextPathReference} from its sentinel map shape.
     *
     * @param map a map expected to contain a single entry keyed by {@link #SENTINEL_KEY}
     *            whose value is a non-blank {@code String}
     * @return the reconstructed reference
     * @throws IllegalArgumentException if the map does not match the expected shape
     */
    @JsonCreator
    public static ContextPathReference fromJson(Map<String, Object> map) {
        Object value = map.get(SENTINEL_KEY);
        if (!(value instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException(
                    "ContextPathReference must be of shape { \"" + SENTINEL_KEY
                            + "\": <non-blank String> }, got " + map);
        }
        return new ContextPathReference(s);
    }
}
