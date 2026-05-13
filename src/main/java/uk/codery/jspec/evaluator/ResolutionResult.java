package uk.codery.jspec.evaluator;

import java.util.List;
import java.util.Map;

/**
 * Outcome of resolving a normalised query against a context document.
 * Either {@link #missingPaths()} is empty (in which case {@link #resolved()}
 * is a fully-resolved query ready for the evaluator) or it lists the
 * unresolved {@code context.<path>} references that prevented resolution.
 */
public record ResolutionResult(Map<String, Object> resolved, List<String> missingPaths) {

    public boolean hasMissingPaths() {
        return !missingPaths.isEmpty();
    }
}
