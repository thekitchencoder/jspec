package uk.codery.jspec.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import uk.codery.jspec.evaluator.EvaluationContext;
import uk.codery.jspec.result.EvaluationResult;

/**
 * Represents a logical criterion used for evaluating documents.
 *
 * <p>Criteria are the building blocks of queries and specifications, and can
 * be composed, queried directly, or referenced. This interface is sealed and
 * permits three specific implementations: {@link QueryCriterion},
 * {@link CompositeCriterion}, and {@link CriterionReference}.
 *
 * <p>{@link QueryCriterion} represents single, query-like conditions.
 * {@link CompositeCriterion} allows logical composition of multiple criteria
 * with AND/OR junctions.
 * {@link CriterionReference} provides a way to reference other criteria by ID.
 *
 * <h3>Serialization:</h3>
 * Criteria are serialized/deserialized as JSON objects with a "type" property
 * that distinguishes between the allowed implementations.
 *
 * <h3>Evaluation:</h3>
 * All criteria must provide an implementation of the {@code evaluate} method
 * to define how they are assessed against a given document in a specific
 * evaluation context.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = QueryCriterion.class, name = "query"),
        @JsonSubTypes.Type(value = CompositeCriterion.class, name = "composite"),
        @JsonSubTypes.Type(value = CriterionReference.class, name = "reference")
})
public sealed interface Criterion
        permits QueryCriterion, CompositeCriterion, CriterionReference {

    /**
     * Returns the unique identifier for this criterion.
     *
     * @return the criterion ID (never null)
     */
    String id();

    /**
     * Returns a new CriterionReference for this criterion.
     *
     * @return the CriterionReference (never null)
     */
    default Criterion ref() {
        return new CriterionReference(id());
    }

    /**
     * Evaluates this criterion against a document.
     *
     * <p>The evaluation context provides:
     * <ul>
     *   <li>Access to the {@link uk.codery.jspec.evaluator.CriterionEvaluator}</li>
     *   <li>Result caching for referenced criteria (evaluate once, reuse many times)</li>
     * </ul>
     *
     * <p><b>Implementation Notes:</b>
     * <ul>
     *   <li>{@link QueryCriterion} - Delegates to evaluator for MongoDB-style query matching</li>
     *   <li>{@link CompositeCriterion} - Recursively evaluates children, applies junction logic</li>
     *   <li>{@link CriterionReference} - Looks up cached result by ID</li>
     * </ul>
     *
     * @param document the document to evaluate against
     * @param context the evaluation context (provides evaluator and result cache)
     * @return the evaluation result (never null)
     */
    EvaluationResult evaluate(Object document, EvaluationContext context);
}
