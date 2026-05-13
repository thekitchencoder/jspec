package uk.codery.jspec.model;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One-pass walk that converts raw {@code { "$contextPath": "..." }} maps inside
 * a query tree into {@link ContextPathReference} instances.
 *
 * <p>Runs once at {@link uk.codery.jspec.evaluator.SpecificationEvaluator}
 * construction; per-evaluation resolution then operates on typed references
 * instead of repeatedly pattern-matching raw map shapes.
 *
 * <p>If a map contains the {@link ContextPathReference#SENTINEL_KEY} but doesn't
 * meet the strict shape (sole key, non-blank String value), a warning is logged.
 * This catches user mistakes such as {@code "$contextPath": null} (from a
 * YAML key with no value) or {@code "$contextPath": 42} that would otherwise
 * be silently treated as a plain query map.
 */
@Slf4j
public final class SpecificationNormaliser {

    private SpecificationNormaliser() {}

    /**
     * Recursively normalises any value found in a query tree.
     * Returns immutable structures so that normalised specs cannot be mutated
     * by evaluation code.
     */
    @SuppressWarnings("unchecked")
    public static Object normalise(Object value) {
        if (value instanceof Map<?, ?> map) {
            // Whole-map check first — sentinel shape collapses to a reference.
            var ref = ContextPathReferences.fromOperand(map);
            if (ref.isPresent()) return ref.get();

            // Near-miss: sentinel key present but shape rejected.
            if (map.containsKey(ContextPathReference.SENTINEL_KEY)) {
                log.warn("Map contains {} but is not a valid context-path reference (extra keys or non-String value); treating as a plain query map: {}",
                        ContextPathReference.SENTINEL_KEY, map);
            }

            return ((Map<String, Object>) map).entrySet().stream()
                    .collect(Collectors.collectingAndThen(
                            Collectors.toUnmodifiableMap(
                                    Map.Entry::getKey,
                                    e -> normalise(e.getValue())),
                            Map::copyOf));
        }
        if (value instanceof List<?> list) {
            return list.stream().map(SpecificationNormaliser::normalise).toList();
        }
        return value;
    }
}
