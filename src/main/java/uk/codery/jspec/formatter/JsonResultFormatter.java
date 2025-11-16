package uk.codery.jspec.formatter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import uk.codery.jspec.result.EvaluationOutcome;

/**
 * Formats evaluation results as JSON.
 *
 * <p>Produces JSON output that can be:
 * <ul>
 *   <li>Pretty-printed for human readability</li>
 *   <li>Compact for machine processing</li>
 *   <li>Parsed back into Java objects</li>
 * </ul>
 *
 * <h2>Output Example (Pretty-Printed)</h2>
 * <pre>{@code
 * {
 *   "specificationId" : "order-validation",
 *   "results" : [ {
 *     "criterion" : {
 *       "id" : "age-check",
 *       "query" : {
 *         "age" : {
 *           "$gte" : 18
 *         }
 *       }
 *     },
 *     "state" : "MATCHED",
 *     "missingPaths" : [ ],
 *     "failureReason" : null
 *   } ],
 *   "summary" : {
 *     "total" : 1,
 *     "matched" : 1,
 *     "notMatched" : 0,
 *     "undetermined" : 0,
 *     "fullyDetermined" : true
 *   }
 * }
 * }</pre>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Pretty-Printed JSON</h3>
 * <pre>{@code
 * ResultFormatter formatter = new JsonResultFormatter(true);
 * String json = formatter.format(outcome);
 * System.out.println(json);
 * }</pre>
 *
 * <h3>Compact JSON</h3>
 * <pre>{@code
 * ResultFormatter formatter = new JsonResultFormatter(false);
 * String json = formatter.format(outcome);
 * // {"specificationId":"order-validation","results":[...],"summary":{...}}
 * }</pre>
 *
 * <h3>Default (Pretty-Printed)</h3>
 * <pre>{@code
 * ResultFormatter formatter = new JsonResultFormatter();
 * String json = formatter.format(outcome);
 * }</pre>
 *
 * @since 0.2.0
 */
@Slf4j
public class JsonResultFormatter implements ResultFormatter {

    private final ObjectMapper objectMapper;
    private final boolean prettyPrint;

    /**
     * Creates a JSON formatter with pretty-printing enabled.
     */
    public JsonResultFormatter() {
        this(true);
    }

    /**
     * Creates a JSON formatter with configurable pretty-printing.
     *
     * @param prettyPrint true to enable pretty-printing, false for compact output
     */
    public JsonResultFormatter(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
        this.objectMapper = new ObjectMapper();
        if (prettyPrint) {
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        }
    }

    /**
     * Creates a JSON formatter with a custom ObjectMapper.
     *
     * <p>Allows full control over Jackson configuration.
     *
     * @param objectMapper the ObjectMapper to use for serialization
     */
    public JsonResultFormatter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.prettyPrint = objectMapper.isEnabled(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public String format(EvaluationOutcome outcome) {
        try {
            log.debug("Formatting evaluation outcome as JSON (prettyPrint={})", prettyPrint);
            return objectMapper.writeValueAsString(outcome);
        } catch (JsonProcessingException e) {
            log.error("Failed to format evaluation outcome as JSON", e);
            throw new FormatterException("Failed to format evaluation outcome as JSON", e);
        }
    }

    @Override
    public String formatType() {
        return "json";
    }

    /**
     * Returns the underlying ObjectMapper.
     *
     * @return the ObjectMapper instance
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Returns whether pretty-printing is enabled.
     *
     * @return true if pretty-printing is enabled
     */
    public boolean isPrettyPrint() {
        return prettyPrint;
    }
}
