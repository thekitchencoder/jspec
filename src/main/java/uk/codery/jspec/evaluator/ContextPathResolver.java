package uk.codery.jspec.evaluator;

import uk.codery.jspec.model.ContextPathReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Walks a normalised query tree, replacing every {@link ContextPathReference}
 * with the value found at its path in the supplied context document. A reference
 * that cannot be resolved is replaced with an {@link UnresolvedReference} sentinel
 * rather than failing fast; the evaluator then decides, per operator, whether that
 * miss actually influences the criterion's outcome (e.g. a missing operand in one
 * {@code $or} branch does not taint a sibling branch that matched).
 */
public final class ContextPathResolver {

    private static final String CONTEXT_PREFIX = "context.";

    private ContextPathResolver() {}

    /**
     * Returns a copy of {@code query} with every {@link ContextPathReference}
     * resolved against {@code contextDoc} (or replaced with an
     * {@link UnresolvedReference} sentinel where the path is absent). When the query
     * contains no references the original map instance is returned unchanged, so
     * plain specs incur zero per-evaluation reallocation.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> resolve(Map<String, Object> query, Object contextDoc) {
        return (Map<String, Object>) walk(query, contextDoc);
    }

    private static Object walk(Object value, Object contextDoc) {
        if (value instanceof ContextPathReference ref) {
            return lookup(ref, contextDoc);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>(map.size());
            boolean rewritten = false;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                Object original = e.getValue();
                Object walked = walk(original, contextDoc);
                rewritten |= walked != original;
                out.put((String) e.getKey(), walked);
            }
            // Fast path: no descendant was a ContextPathReference, so the input
            // tree (already immutable post-normalisation) is returned unchanged —
            // plain specs incur zero per-evaluation reallocation.
            if (!rewritten) return value;
            // LinkedHashMap + unmodifiableMap (not Map.copyOf) so that null leaf values
            // (e.g. {$eq: null}) pass through — Map.copyOf would NPE.
            return Collections.unmodifiableMap(out);
        }
        if (value instanceof List<?> list) {
            boolean rewritten = false;
            List<Object> out = new ArrayList<>(list.size());
            for (Object v : list) {
                Object walked = walk(v, contextDoc);
                rewritten |= walked != v;
                out.add(walked);
            }
            return rewritten ? Collections.unmodifiableList(out) : value;
        }
        return value;
    }

    private static Object lookup(ContextPathReference ref, Object contextDoc) {
        Object current = contextDoc;
        for (String segment : ref.path().split("\\.")) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(segment)) {
                // Leave a typed sentinel (not null — null is a valid resolved value).
                return new UnresolvedReference(CONTEXT_PREFIX + ref.path());
            }
            current = map.get(segment);
        }
        // A null value at the path is itself a present entry — treat as resolved-to-null
        // so operators like $exists / $eq:null work. If the user wants "missing", they
        // shouldn't put the key in the context doc at all.
        return current;
    }
}
