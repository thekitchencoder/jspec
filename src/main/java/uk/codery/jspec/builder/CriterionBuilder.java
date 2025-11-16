package uk.codery.jspec.builder;

import uk.codery.jspec.model.QueryCriterion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fluent builder for creating {@link QueryCriterion} instances with a readable API.
 *
 * <p>This builder provides a more intuitive alternative to manually constructing
 * Map-based queries. Instead of nested Maps, you can use a fluent API that clearly
 * expresses the criterion's intent.
 *
 * <h2>Basic Usage</h2>
 *
 * <h3>Simple Equality Check</h3>
 * <pre>{@code
 * // Before (using Map)
 * QueryCriterion criterion = new QueryCriterion("status-check",
 *     Map.of("status", Map.of("$eq", "active")));
 *
 * // After (using builder)
 * QueryCriterion criterion = QueryCriterion.builder()
 *     .id("status-check")
 *     .field("status").eq("active")
 *     .build();
 * }</pre>
 *
 * <h3>Comparison Operators</h3>
 * <pre>{@code
 * // Age greater than or equal to 18
 * QueryCriterion criterion = QueryCriterion.builder()
 *     .id("age-check")
 *     .field("age").gte(18)
 *     .build();
 *
 * // Price less than 100
 * QueryCriterion criterion = QueryCriterion.builder()
 *     .id("price-check")
 *     .field("price").lt(100.0)
 *     .build();
 * }</pre>
 *
 * <h3>Collection Operators</h3>
 * <pre>{@code
 * // Status in list of values
 * QueryCriterion criterion = QueryCriterion.builder()
 *     .id("status-check")
 *     .field("status").in("active", "pending", "approved")
 *     .build();
 *
 * // Tags contains all required values
 * QueryCriterion criterion = QueryCriterion.builder()
 *     .id("tags-check")
 *     .field("tags").all("important", "urgent")
 *     .build();
 *
 * // Array has specific size
 * QueryCriterion criterion = QueryCriterion.builder()
 *     .id("items-check")
 *     .field("items").size(5)
 *     .build();
 * }</pre>
 *
 * <h3>Advanced Operators</h3>
 * <pre>{@code
 * // Field exists
 * QueryCriterion criterion = QueryCriterion.builder()
 *     .id("email-check")
 *     .field("email").exists(true)
 *     .build();
 *
 * // Type checking
 * QueryCriterion criterion = QueryCriterion.builder()
 *     .id("count-check")
 *     .field("count").type("number")
 *     .build();
 *
 * // Regex matching
 * QueryCriterion criterion = QueryCriterion.builder()
 *     .id("email-format")
 *     .field("email").regex("^[\\w.]+@[\\w.]+\\.[a-z]+$")
 *     .build();
 * }</pre>
 *
 * <h3>Multiple Conditions on Same Field</h3>
 * <pre>{@code
 * // Age between 18 and 65
 * QueryCriterion criterion = QueryCriterion.builder()
 *     .id("age-range")
 *     .field("age").gte(18).and().lte(65)
 *     .build();
 *
 * // Price greater than 10 and less than 100
 * QueryCriterion criterion = QueryCriterion.builder()
 *     .id("price-range")
 *     .field("price").gt(10).and().lt(100)
 *     .build();
 * }</pre>
 *
 * <h3>Multiple Fields</h3>
 * <pre>{@code
 * // Check multiple fields
 * QueryCriterion criterion = QueryCriterion.builder()
 *     .id("user-validation")
 *     .field("age").gte(18)
 *     .field("status").eq("active")
 *     .field("email").exists(true)
 *     .build();
 * }</pre>
 *
 * <h3>Nested Fields (Dot Notation)</h3>
 * <pre>{@code
 * // Check nested field
 * QueryCriterion criterion = QueryCriterion.builder()
 *     .id("address-check")
 *     .field("address.city").eq("London")
 *     .field("address.postalCode").regex("^[A-Z]{1,2}\\d")
 *     .build();
 * }</pre>
 *
 * <h3>Custom Operators</h3>
 * <pre>{@code
 * // Use custom operator
 * QueryCriterion criterion = QueryCriterion.builder()
 *     .id("username-length")
 *     .field("username").operator("$length", 8)
 *     .build();
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p><b>Not thread-safe:</b> Builder instances should not be shared across threads.
 * Each thread should create its own builder instance.
 *
 * @see QueryCriterion
 * @see uk.codery.jspec.evaluator.CriterionEvaluator
 * @since 0.2.0
 */
public class CriterionBuilder {

    private String id;
    private final Map<String, Object> query = new HashMap<>();

    /**
     * Creates a new CriterionBuilder instance.
     *
     * <p>Use {@code QueryCriterion.builder()} instead of calling this constructor directly.
     */
    public CriterionBuilder() {
        // Package-private constructor
    }

    /**
     * Sets the criterion ID.
     *
     * <p>The ID is used to identify this criterion in evaluation results.
     *
     * @param id the criterion identifier
     * @return this builder for method chaining
     * @throws IllegalArgumentException if id is null or empty
     */
    public CriterionBuilder id(String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Criterion ID cannot be null or empty");
        }
        this.id = id;
        return this;
    }

    /**
     * Starts building a query for the specified field.
     *
     * <p>Returns a {@link FieldBuilder} that provides fluent methods for all operators.
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * Criterion criterion = Criterion.builder()
     *     .id("age-check")
     *     .field("age").gte(18)
     *     .build();
     * }</pre>
     *
     * @param fieldName the field name (supports dot notation for nested fields)
     * @return a FieldBuilder for the specified field
     * @throws IllegalArgumentException if fieldName is null or empty
     */
    public FieldBuilder field(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            throw new IllegalArgumentException("Field name cannot be null or empty");
        }
        return new FieldBuilder(this, fieldName);
    }

    /**
     * Builds the QueryCriterion instance.
     *
     * @return the constructed QueryCriterion
     * @throws IllegalStateException if id is not set or no fields are defined
     */
    public QueryCriterion build() {
        if (id == null || id.isEmpty()) {
            throw new IllegalStateException("QueryCriterion ID must be set before building");
        }
        if (query.isEmpty()) {
            throw new IllegalStateException("At least one field condition must be defined");
        }
        return new QueryCriterion(id, new HashMap<>(query));
    }

    /**
     * Fluent builder for field-level queries.
     *
     * <p>Provides methods for all MongoDB-style operators and allows chaining
     * multiple conditions on the same field.
     */
    public static class FieldBuilder {
        private final CriterionBuilder parent;
        private final String fieldName;
        private final Map<String, Object> fieldQuery = new HashMap<>();

        FieldBuilder(CriterionBuilder parent, String fieldName) {
            this.parent = parent;
            this.fieldName = fieldName;
        }

        // ==================== Comparison Operators ====================

        /**
         * Adds an equality ($eq) condition.
         *
         * @param value the value to compare against
         * @return the parent CriterionBuilder for chaining
         */
        public CriterionBuilder eq(Object value) {
            return operator("$eq", value);
        }

        /**
         * Adds a not equal ($ne) condition.
         *
         * @param value the value to compare against
         * @return the parent CriterionBuilder for chaining
         */
        public CriterionBuilder ne(Object value) {
            return operator("$ne", value);
        }

        /**
         * Adds a greater than ($gt) condition.
         *
         * @param value the value to compare against
         * @return this FieldBuilder for chaining conditions on the same field
         */
        public FieldBuilder gt(Object value) {
            fieldQuery.put("$gt", value);
            return this;
        }

        /**
         * Adds a greater than or equal ($gte) condition.
         *
         * @param value the value to compare against
         * @return this FieldBuilder for chaining conditions on the same field
         */
        public FieldBuilder gte(Object value) {
            fieldQuery.put("$gte", value);
            return this;
        }

        /**
         * Adds a less than ($lt) condition.
         *
         * @param value the value to compare against
         * @return this FieldBuilder for chaining conditions on the same field
         */
        public FieldBuilder lt(Object value) {
            fieldQuery.put("$lt", value);
            return this;
        }

        /**
         * Adds a less than or equal ($lte) condition.
         *
         * @param value the value to compare against
         * @return this FieldBuilder for chaining conditions on the same field
         */
        public FieldBuilder lte(Object value) {
            fieldQuery.put("$lte", value);
            return this;
        }

        // ==================== Collection Operators ====================

        /**
         * Adds an in ($in) condition - value must be in the provided list.
         *
         * @param values the allowed values
         * @return the parent CriterionBuilder for chaining
         */
        public CriterionBuilder in(Object... values) {
            return operator("$in", List.of(values));
        }

        /**
         * Adds a not in ($nin) condition - value must not be in the provided list.
         *
         * @param values the disallowed values
         * @return the parent CriterionBuilder for chaining
         */
        public CriterionBuilder nin(Object... values) {
            return operator("$nin", List.of(values));
        }

        /**
         * Adds an all ($all) condition - array must contain all specified values.
         *
         * @param values the values that must all be present
         * @return the parent CriterionBuilder for chaining
         */
        public CriterionBuilder all(Object... values) {
            return operator("$all", List.of(values));
        }

        /**
         * Adds a size ($size) condition - array must have the specified length.
         *
         * @param size the expected array size
         * @return the parent CriterionBuilder for chaining
         */
        public CriterionBuilder size(int size) {
            return operator("$size", size);
        }

        /**
         * Adds an elemMatch ($elemMatch) condition.
         *
         * @param elementQuery the query that array elements must match
         * @return the parent CriterionBuilder for chaining
         */
        public CriterionBuilder elemMatch(Map<String, Object> elementQuery) {
            return operator("$elemMatch", elementQuery);
        }

        // ==================== Advanced Operators ====================

        /**
         * Adds an exists ($exists) condition - field must exist (or not exist).
         *
         * @param shouldExist true if field must exist, false if it must not exist
         * @return the parent CriterionBuilder for chaining
         */
        public CriterionBuilder exists(boolean shouldExist) {
            return operator("$exists", shouldExist);
        }

        /**
         * Adds a type ($type) condition - field must be of specified type.
         *
         * <p>Valid types: "string", "number", "boolean", "array", "object", "null"
         *
         * @param typeName the expected type name
         * @return the parent CriterionBuilder for chaining
         */
        public CriterionBuilder type(String typeName) {
            return operator("$type", typeName);
        }

        /**
         * Adds a regex ($regex) condition - field must match the pattern.
         *
         * @param pattern the regular expression pattern
         * @return the parent CriterionBuilder for chaining
         */
        public CriterionBuilder regex(String pattern) {
            return operator("$regex", pattern);
        }

        // ==================== Custom Operators ====================

        /**
         * Adds a custom operator condition.
         *
         * <p>Use this for custom operators registered via OperatorRegistry.
         *
         * @param operatorName the operator name (e.g., "$length", "$custom")
         * @param operand the operator operand
         * @return the parent CriterionBuilder for chaining
         */
        public CriterionBuilder operator(String operatorName, Object operand) {
            fieldQuery.put(operatorName, operand);
            parent.query.put(fieldName, new HashMap<>(fieldQuery));
            return parent;
        }

        // ==================== Chaining Support ====================

        /**
         * Allows chaining multiple conditions on the same field.
         *
         * <p>Use this when you want to add multiple operators to the same field,
         * such as creating a range query.
         *
         * <h3>Example:</h3>
         * <pre>{@code
         * Criterion criterion = Criterion.builder()
         *     .id("age-range")
         *     .field("age").gte(18).and().lte(65)
         *     .build();
         * }</pre>
         *
         * @return this FieldBuilder for continued chaining
         */
        public FieldBuilder and() {
            parent.query.put(fieldName, new HashMap<>(fieldQuery));
            return this;
        }

        /**
         * Starts building a query for another field.
         *
         * <p>Convenience method to chain multiple field definitions without
         * going back through the parent builder.
         *
         * <h3>Example:</h3>
         * <pre>{@code
         * Criterion criterion = Criterion.builder()
         *     .id("validation")
         *     .field("age").gte(18)
         *     .field("status").eq("active")
         *     .field("email").exists(true)
         *     .build();
         * }</pre>
         *
         * @param fieldName the next field name to query
         * @return a new FieldBuilder for the specified field
         */
        public FieldBuilder field(String fieldName) {
            // Finalize current field first
            parent.query.put(this.fieldName, new HashMap<>(fieldQuery));
            // Start new field
            return parent.field(fieldName);
        }

        /**
         * Builds the Criterion instance.
         *
         * <p>Convenience method that finalizes the current field and builds
         * the criterion without needing to return to the parent builder.
         *
         * <h3>Example:</h3>
         * <pre>{@code
         * Criterion criterion = Criterion.builder()
         *     .id("age-check")
         *     .field("age").gte(18)
         *     .build();
         * }</pre>
         *
         * @return the constructed QueryCriterion
         */
        public QueryCriterion build() {
            // Finalize current field
            parent.query.put(fieldName, new HashMap<>(fieldQuery));
            // Delegate to parent
            return parent.build();
        }
    }
}
