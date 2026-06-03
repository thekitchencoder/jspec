package uk.codery.jspec.evaluator;

import uk.codery.jspec.model.ContextPathReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Walks a normalised query tree, replacing every {@link ContextPathReference}
 * with the value found at its path in the supplied context document.
 * Collects any unresolved references rather than failing fast — that way the
 * caller can report all missing paths in one go.
 */
public final class ContextPathResolver {

    private static final String CONTEXT_PREFIX = "context.";

    private ContextPathResolver() {}

    @SuppressWarnings("unchecked")
    public static ResolutionResult resolve(Map<String, Object> query, Object contextDoc) {
        List<String> missing = new ArrayList<>();
        Object resolved = walk(query, contextDoc, missing);
        return new ResolutionResult((Map<String, Object>) resolved, List.copyOf(missing));
    }

    private static Object walk(Object value, Object contextDoc, List<String> missing) {
        if (value instanceof ContextPathReference ref) {
            return lookup(ref, contextDoc, missing);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>(map.size());
            boolean rewritten = false;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                Object original = e.getValue();
                Object walked = walk(original, contextDoc, missing);
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
                Object walked = walk(v, contextDoc, missing);
                rewritten |= walked != v;
                out.add(walked);
            }
            return rewritten ? Collections.unmodifiableList(out) : value;
        }
        return value;
    }

    private static Object lookup(ContextPathReference ref, Object contextDoc, List<String> missing) {
        Object current = contextDoc;
        for (String segment : ref.path().split("\\.")) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(segment)) {
                missing.add(CONTEXT_PREFIX + ref.path());
                return null;  // sentinel; caller short-circuits via hasMissingPaths()
            }
            current = map.get(segment);
        }
        // A null value at the path is itself a present entry — treat as resolved-to-null
        // so operators like $exists / $eq:null work. If the user wants "missing", they
        // shouldn't put the key in the context doc at all.
        return current;
    }
}
