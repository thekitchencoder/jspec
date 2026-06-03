package uk.codery.jspec.evaluator;

/**
 * Sentinel left in a resolved query tree by {@link ContextPathResolver} in place of a
 * {@link uk.codery.jspec.model.ContextPathReference} whose path could not be resolved
 * against the context document.
 *
 * <p>Distinct from a literal {@code null} (which is a legitimate <em>resolved</em>
 * value — see the present-but-null contract in CLAUDE.md). When the
 * {@link CriterionEvaluator} encounters this sentinel as an operator operand it marks
 * that operator {@code UNDETERMINED} and records {@link #path()} as a missing path —
 * but only if the surrounding Kleene combination actually depends on it (e.g. it does
 * <em>not</em> taint a {@code $or} whose other branch already matched).
 *
 * @param path the already-prefixed {@code context.<dot.path>} that failed to resolve
 */
// Package-private by design: this sentinel is an internal evaluation detail and must
// never escape the evaluator package or appear in a public result.
record UnresolvedReference(String path) {}
