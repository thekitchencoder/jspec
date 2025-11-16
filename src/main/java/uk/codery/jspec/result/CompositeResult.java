package uk.codery.jspec.result;

import uk.codery.jspec.model.CompositeCriterion;
import uk.codery.jspec.model.Junction;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Result of evaluating a {@link CompositeCriterion} against a document.
 *
 * <p>A composite result contains:
 * <ul>
 *   <li>The composite criterion that was evaluated</li>
 *   <li>The tri-state evaluation result</li>
 *   <li>Individual results for each child criterion (can include queries, composites, references)</li>
 * </ul>
 *
 * <h2>State Calculation</h2>
 *
 * <h3>AND Junction</h3>
 * <ul>
 *   <li><b>MATCHED:</b> All children are MATCHED</li>
 *   <li><b>NOT_MATCHED:</b> Any child is NOT_MATCHED</li>
 *   <li><b>UNDETERMINED:</b> No NOT_MATCHED children, but at least one UNDETERMINED</li>
 * </ul>
 *
 * <h3>OR Junction</h3>
 * <ul>
 *   <li><b>MATCHED:</b> Any child is MATCHED</li>
 *   <li><b>NOT_MATCHED:</b> All children are NOT_MATCHED</li>
 *   <li><b>UNDETERMINED:</b> No MATCHED children, but at least one UNDETERMINED</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Checking Composite Result</h3>
 * <pre>{@code
 * if (result instanceof CompositeResult composite) {
 *     System.out.println("Junction: " + composite.junction());
 *     System.out.println("State: " + composite.state());
 *     System.out.println("Children: " + composite.childResults().size());
 *
 *     // Inspect child results
 *     for (EvaluationResult child : composite.childResults()) {
 *         System.out.println("  - " + child.id() + ": " + child.state());
 *     }
 * }
 * }</pre>
 *
 * <h3>Pattern Matching (Java 21+)</h3>
 * <pre>{@code
 * switch (result) {
 *     case CompositeResult composite when composite.state() == MATCHED ->
 *         System.out.println("Composite " + composite.id() + " matched!");
 *
 *     case CompositeResult composite ->
 *         System.out.println("Composite " + composite.id() + " failed: " + composite.reason());
 *
 *     default -> System.out.println("Not a composite result");
 * }
 * }</pre>
 *
 * <h3>Analyzing Failures</h3>
 * <pre>{@code
 * if (composite.state() != MATCHED) {
 *     long failedCount = composite.childResults().stream()
 *         .filter(r -> r.state() != MATCHED)
 *         .count();
 *
 *     System.out.println("Composite failed with " + failedCount + " failed children");
 *     System.out.println("Junction: " + composite.junction());
 *     System.out.println("Reason: " + composite.reason());
 * }
 * }</pre>
 *
 * @param criterion the composite criterion that was evaluated
 * @param state the tri-state evaluation result (calculated from child states)
 * @param childResults the individual results for each child criterion
 * @see CompositeCriterion
 * @see EvaluationState
 * @see Junction
 * @since 0.2.0
 */
public record CompositeResult(
        CompositeCriterion criterion,
        EvaluationState state,
        List<EvaluationResult> childResults) implements EvaluationResult {

    /**
     * Ensures the child results list is immutable.
     */
    public CompositeResult {
        childResults = childResults != null ? List.copyOf(childResults) : Collections.emptyList();
    }

    @Override
    public String id() {
        return criterion.id();
    }

    /**
     * Returns the junction used to combine child criteria.
     *
     * @return AND or OR
     */
    public Junction junction() {
        return criterion.junction();
    }

    @Override
    public boolean matched() {
        return state == EvaluationState.MATCHED;
    }

    /**
     * Returns a combined reason string from all child results.
     *
     * <p>For MATCHED composites, returns null.
     * For failed composites, provides details about which children failed and why.
     *
     * <h3>Example Output:</h3>
     * <ul>
     *   <li>"AND composite failed: 2 matched, 1 not matched, 0 undetermined"</li>
     *   <li>"OR composite failed: all children failed (3 not matched, 0 undetermined)"</li>
     *   <li>"Composite has undetermined children: Missing data at: age, email"</li>
     * </ul>
     *
     * @return combined reason from children, or null if matched
     */
    @Override
    public String reason() {
        if (state == EvaluationState.MATCHED) {
            return null;
        }

        long matchedCount = childResults.stream()
                .filter(r -> r.state() == EvaluationState.MATCHED)
                .count();

        long notMatchedCount = childResults.stream()
                .filter(r -> r.state() == EvaluationState.NOT_MATCHED)
                .count();

        long undeterminedCount = childResults.stream()
                .filter(r -> r.state() == EvaluationState.UNDETERMINED)
                .count();

        String summary = switch (junction()) {
            case AND -> String.format(
                    "AND composite failed: %d matched, %d not matched, %d undetermined",
                    matchedCount, notMatchedCount, undeterminedCount);
            case OR -> String.format(
                    "OR composite failed: all children failed (%d not matched, %d undetermined)",
                    notMatchedCount, undeterminedCount);
        };

        // Include child reasons for failed/undetermined children
        String childReasons = childResults.stream()
                .filter(r -> r.state() != EvaluationState.MATCHED)
                .map(r -> r.id() + ": " + (r.reason() != null ? r.reason() : "not matched"))
                .collect(Collectors.joining("; "));

        return childReasons.isEmpty() ? summary : summary + " [" + childReasons + "]";
    }

    /**
     * Returns true if the evaluation was deterministic (not UNDETERMINED).
     *
     * @return true if state is MATCHED or NOT_MATCHED
     */
    public boolean isDetermined() {
        return state != EvaluationState.UNDETERMINED;
    }

    /**
     * Returns statistics about child results.
     *
     * @return statistics record
     */
    public Statistics statistics() {
        long matched = childResults.stream()
                .filter(r -> r.state() == EvaluationState.MATCHED)
                .count();
        long notMatched = childResults.stream()
                .filter(r -> r.state() == EvaluationState.NOT_MATCHED)
                .count();
        long undetermined = childResults.stream()
                .filter(r -> r.state() == EvaluationState.UNDETERMINED)
                .count();

        return new Statistics(
                childResults.size(),
                matched,
                notMatched,
                undetermined
        );
    }

    /**
     * Statistics about child results.
     */
    public record Statistics(
            long total,
            long matched,
            long notMatched,
            long undetermined) {
    }

    // TODO external formatters (YAML,JSON,Text,etc) rather than YAML embedded in the toString method
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(criterion.id()).append(" (composite):\n");
        sb.append("  match: ").append(matched()).append("\n");
        sb.append("  junction: ").append(junction()).append("\n");
        sb.append("  state: ").append(state).append("\n");
        sb.append("  children: ").append(childResults.size()).append("\n");

        Statistics stats = statistics();
        sb.append("  stats: matched=").append(stats.matched())
                .append(", not_matched=").append(stats.notMatched())
                .append(", undetermined=").append(stats.undetermined()).append("\n");

        if (state != EvaluationState.MATCHED) {
            sb.append("  reason: \"").append(reason()).append("\"\n");
        }

        return sb.toString();
    }
}
