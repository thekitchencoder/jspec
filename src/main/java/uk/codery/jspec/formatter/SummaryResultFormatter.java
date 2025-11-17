package uk.codery.jspec.formatter;

import lombok.extern.slf4j.Slf4j;
import uk.codery.jspec.result.EvaluationOutcome;
import uk.codery.jspec.result.EvaluationResult;

import static java.util.function.Predicate.not;

@Slf4j
public record SummaryResultFormatter(boolean showFailures) implements ResultFormatter {

    /**
     * Creates a Summary formatter with default verbosity (non-showFailures).
     */
    public SummaryResultFormatter() {
        this(false);
    }

    @Override
    public String format(EvaluationOutcome outcome) {
        log.debug("Formatting evaluation outcome as summary (showFailures={})", showFailures);

        long passedQueries = outcome.queryResults().stream()
                .filter(EvaluationResult::matched)
                .count();
        long failedQueries = outcome.queryResults().stream()
                .filter(not(EvaluationResult::matched))
                .count();
        long passedComposites = outcome.compositeResults().stream()
                .filter(EvaluationResult::matched)
                .count();
        long failedComposites = outcome.compositeResults().stream()
                .filter(not(EvaluationResult::matched))
                .count();

        StringBuilder sb = new StringBuilder();

        // Header
        sb.append(outcome.specificationId()).append(" evaluation summary.").append("\n");

        sb.append("Queries: ").append(passedQueries + failedQueries).append(" total, ")
                .append(passedQueries).append(" passed, ")
                .append(failedQueries).append(" failed\n");
        sb.append("Composites: ").append(passedComposites + failedComposites).append(" total, ")
                .append(passedComposites).append(" passed, ")
                .append(failedComposites).append(" failed\n");

        if(showFailures) {
            if (failedQueries > 0) {
                sb.append("\nFailed Queries:\n");
                outcome.queryResults().stream()
                        .filter(not(EvaluationResult::matched))
                        .forEach(r -> sb.append("  ").append(r.id()).append(": ").append( r.reason()).append("\n"));
            }

            if (failedComposites > 0) {
                sb.append("\nFailed Composites:\n");
                outcome.compositeResults().stream()
                        .filter(not(EvaluationResult::matched))
                        .forEach(composite -> {
                            sb.append("  ").append(composite.id()).append("\n");
                            composite.childResults().stream()
                                    .filter(not(EvaluationResult::matched))
                                    .forEach(child -> sb.append("    - ").append(child.id()).append( ": ").append(child.reason()).append("\n"));
                        });
            }
        }
        return sb.toString();
    }

    @Override
    public String formatType() {
        return "summary";
    }
}
