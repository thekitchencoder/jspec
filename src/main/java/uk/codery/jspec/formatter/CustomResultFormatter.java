package uk.codery.jspec.formatter;

import lombok.extern.slf4j.Slf4j;
import uk.codery.jspec.result.*;


/**
 * @param verbose -- GETTER --
 *                Returns whether showFailures mode is enabled.
 */
@Slf4j
public record CustomResultFormatter(boolean verbose) implements ResultFormatter {

    private static final String INDENT = "  ";

    /**
     * Creates a text formatter with default verbosity (non-showFailures).
     */
    public CustomResultFormatter() {
        this(false);
    }

    @Override
    public String format(EvaluationOutcome outcome) {
        log.debug("Formatting evaluation outcome as text (showFailures={})", verbose);

        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("specification: ").append(outcome.specificationId()).append("\n");
        outcome.results().stream().filter(CompositeResult.class::isInstance)
                .forEach(result -> {
                    formatResult(sb, result, 0);
                    sb.append("\n");
                });

        return sb.toString();
    }

    private void formatResult(StringBuilder sb, EvaluationResult result, int depth) {
        switch (result) {
            case QueryResult query -> formatQueryResult(sb, query, depth);
            case CompositeResult composite -> formatCompositeResult(sb, composite, depth);
            case ReferenceResult reference -> formatReferenceResult(sb, reference, depth);
        }
    }

    private void formatQueryResult(StringBuilder sb, QueryResult result, int depth) {
        sb.append(INDENT.repeat(depth)).append(result.id()).append(":\n");
        String indent = INDENT.repeat(depth + 1);
        sb.append(indent).append("match: ").append(result.matched()).append(":\n");
        sb.append(indent).append("state: ").append(result.state()).append(":\n");
        if (result.reason() != null) {
            sb.append(indent).append("reason: ").append(result.reason()).append("\n");
        }
        if (verbose && !result.missingPaths().isEmpty()) {
            sb.append(indent).append("missing: ")
                    .append(String.join(", ", result.missingPaths())).append("\n");
        }
    }

    private void formatCompositeResult(StringBuilder sb, CompositeResult result, int depth) {
        String indent = INDENT.repeat(depth + 1);
        sb.append(result.id()).append(":\n");

        sb.append(indent).append("junction: ").append(result.junction()).append("\n");
        sb.append(indent).append("match: ").append(result.matched()).append(":\n");
        sb.append(indent).append("state: ").append(result.state()).append(":\n");

        CompositeResult.Statistics stats = result.statistics();
        sb.append(indent).append("stats: {matched: ").append(stats.matched())
                .append(", not_matched: ").append(stats.notMatched())
                .append(", undetermined: ").append(stats.undetermined()).append("}\n");

        if (result.reason() != null) {
            sb.append(indent).append("reason: ").append(result.reason()).append("\n");
        }

        if (verbose) {
            sb.append(indent).append("criteria: ").append("\n");
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

    @Override
    public String formatType() {
        return "text";
    }

}
