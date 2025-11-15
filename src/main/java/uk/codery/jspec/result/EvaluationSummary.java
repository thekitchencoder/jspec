package uk.codery.jspec.result;

/**
 * Summary statistics for a specification evaluation.
 *
 * <p>Tracks the count of criteria in each evaluation state and provides
 * a flag to quickly determine if the evaluation was complete and deterministic.
 *
 * <p>The {@code fullyDetermined} flag is {@code true} only when all criteria
 * evaluated successfully (no UNDETERMINED states), allowing callers to
 * assess the confidence level of the evaluation result.
 */
public record EvaluationSummary(
        int total,
        int matched,
        int notMatched,
        int undetermined,
        boolean fullyDetermined) {

    /**
     * Validates that the sum of matched, not matched, and undetermined criteria equals total criteria.
     */
    public EvaluationSummary {
        if (matched + notMatched + undetermined != total) {
            throw new IllegalArgumentException(
                    "Sum of matched (%d), notMatched (%d), and undetermined (%d) must equal total (%d)"
                            .formatted(matched, notMatched, undetermined, total));
        }
    }

    /**
     * Creates a summary from the given evaluation results.
     *
     * @param results the evaluation results to summarize
     * @return a summary with counts for each state
     */
    public static EvaluationSummary from(Iterable<EvaluationResult> results) {
        int total = 0;
        int matched = 0;
        int notMatched = 0;
        int undetermined = 0;

        for (EvaluationResult result : results) {
            total++;
            switch (result.state()) {
                case MATCHED -> matched++;
                case NOT_MATCHED -> notMatched++;
                case UNDETERMINED -> undetermined++;
            }
        }

        return new EvaluationSummary(
                total,
                matched,
                notMatched,
                undetermined,
                undetermined == 0
        );
    }
}
