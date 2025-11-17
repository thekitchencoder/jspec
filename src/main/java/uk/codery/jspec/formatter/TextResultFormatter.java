package uk.codery.jspec.formatter;

import lombok.extern.slf4j.Slf4j;
import uk.codery.jspec.result.*;

/**
 * Formats evaluation results as plain text.
 *
 * <p>Produces human-readable text output with:
 * <ul>
 *   <li>Clear section headers</li>
 *   <li>Indented hierarchical structure</li>
 *   <li>Summary statistics</li>
 *   <li>Detailed result information</li>
 * </ul>
 *
 * <h2>Output Example</h2>
 * <pre>{@code
 * Specification: order-validation
 * ================================================================================
 *
 * SUMMARY
 * ────────────────────────────────────────────────────────────────────────────────
 * Total:          3 criteria
 * Matched:        2 (66.7%)
 * Not Matched:    1 (33.3%)
 * Undetermined:   0 (0.0%)
 * Status:         Fully Determined
 *
 * RESULTS
 * ────────────────────────────────────────────────────────────────────────────────
 *
 * [✓] age-check (MATCHED)
 *     Type: Query
 *     Query: {age={$gte=18}}
 *
 * [✗] country-check (NOT_MATCHED)
 *     Type: Query
 *     Query: {country={$eq=US}}
 *     Reason: Non-matching values at {country={$eq=US}}
 *
 * [✓] eligibility (MATCHED)
 *     Type: Composite (AND)
 *     Children: 2
 *     Statistics: matched=2, not_matched=0, undetermined=0
 * }</pre>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Basic Text Output</h3>
 * <pre>{@code
 * ResultFormatter formatter = new TextResultFormatter();
 * String text = formatter.format(outcome);
 * System.out.println(text);
 * }</pre>
 *
 * <h3>Verbose Mode</h3>
 * <pre>{@code
 * ResultFormatter formatter = new TextResultFormatter(true);
 * String text = formatter.format(outcome);
 * // Includes additional details like missing paths
 * }</pre>
 *
 * @param verbose -- GETTER --
 *                Returns whether showFailures mode is enabled.
 * @since 0.2.0
 */
@Slf4j
public record TextResultFormatter(boolean verbose) implements ResultFormatter {

    private static final String SEPARATOR = "─".repeat(80);
    private static final String HEADER_SEPARATOR = "=".repeat(80);
    private static final String INDENT = "    ";

    /**
     * Creates a text formatter with default verbosity (non-showFailures).
     */
    public TextResultFormatter() {
        this(false);
    }

    /**
     * Creates a text formatter with configurable verbosity.
     *
     * @param verbose true to include additional details (missing paths, etc.)
     */
    public TextResultFormatter {
    }

    @Override
    public String format(EvaluationOutcome outcome) {
        log.debug("Formatting evaluation outcome as text (showFailures={})", verbose);

        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("Specification: ").append(outcome.specificationId()).append("\n");
        sb.append(HEADER_SEPARATOR).append("\n\n");

        // Summary
        formatSummary(sb, outcome.summary());

        // Results
        sb.append("RESULTS\n");
        sb.append(SEPARATOR).append("\n\n");

        for (EvaluationResult result : outcome.results()) {
            formatResult(sb, result, 0);
            sb.append("\n");
        }

        return sb.toString();
    }

    private void formatSummary(StringBuilder sb, EvaluationSummary summary) {
        sb.append("SUMMARY\n");
        sb.append(SEPARATOR).append("\n");

        int total = summary.total();
        sb.append(String.format("Total:          %d %s\n",
                total, total == 1 ? "criterion" : "criteria"));

        if (total > 0) {
            sb.append(String.format("Matched:        %d (%.1f%%)\n",
                    summary.matched(), (summary.matched() * 100.0) / total));
            sb.append(String.format("Not Matched:    %d (%.1f%%)\n",
                    summary.notMatched(), (summary.notMatched() * 100.0) / total));
            sb.append(String.format("Undetermined:   %d (%.1f%%)\n",
                    summary.undetermined(), (summary.undetermined() * 100.0) / total));
        }

        sb.append(String.format("Status:         %s\n",
                summary.fullyDetermined() ? "Fully Determined" : "Partially Determined"));
        sb.append("\n");
    }

    private void formatResult(StringBuilder sb, EvaluationResult result, int depth) {
        String indent = INDENT.repeat(depth);
        String icon = getStateIcon(result.state());

        sb.append(indent).append("[").append(icon).append("] ")
                .append(result.id()).append(" (").append(result.state()).append(")\n");

        switch (result) {
            case QueryResult query -> formatQueryResult(sb, query, depth);
            case CompositeResult composite -> formatCompositeResult(sb, composite, depth);
            case ReferenceResult reference -> formatReferenceResult(sb, reference, depth);
        }
    }

    private void formatQueryResult(StringBuilder sb, QueryResult result, int depth) {
        String indent = INDENT.repeat(depth + 1);

        sb.append(indent).append("Type: Query\n");
        sb.append(indent).append("Query: ").append(result.criterion().query()).append("\n");

        if (verbose && !result.missingPaths().isEmpty()) {
            sb.append(indent).append("Missing Paths: ")
                    .append(String.join(", ", result.missingPaths())).append("\n");
        }

        if (result.reason() != null) {
            sb.append(indent).append("Reason: ").append(result.reason()).append("\n");
        }
    }

    private void formatCompositeResult(StringBuilder sb, CompositeResult result, int depth) {
        String indent = INDENT.repeat(depth + 1);

        sb.append(indent).append("Type: Composite (").append(result.junction()).append(")\n");
        sb.append(indent).append("Children: ").append(result.childResults().size()).append("\n");

        CompositeResult.Statistics stats = result.statistics();
        sb.append(indent).append("Statistics: matched=").append(stats.matched())
                .append(", not_matched=").append(stats.notMatched())
                .append(", undetermined=").append(stats.undetermined()).append("\n");

        if (result.reason() != null) {
            sb.append(indent).append("Reason: ").append(result.reason()).append("\n");
        }

        if (verbose) {
            sb.append(indent).append("Child Results:\n");
            for (EvaluationResult child : result.childResults()) {
                formatResult(sb, child, depth + 2);
            }
        }
    }

    private void formatReferenceResult(StringBuilder sb, ReferenceResult result, int depth) {
        String indent = INDENT.repeat(depth + 1);

        sb.append(indent).append("Type: Reference\n");
        sb.append(indent).append("References: ").append(result.reference().id()).append("\n");

        if (result.reason() != null) {
            sb.append(indent).append("Reason: ").append(result.reason()).append("\n");
        }

        if (verbose) {
            sb.append(indent).append("Referenced Result:\n");
            formatResult(sb, result.referencedResult(), depth + 2);
        }
    }

    private String getStateIcon(EvaluationState state) {
        return switch (state) {
            case MATCHED -> "✓";
            case NOT_MATCHED -> "✗";
            case UNDETERMINED -> "?";
        };
    }

    @Override
    public String formatType() {
        return "text";
    }

}
