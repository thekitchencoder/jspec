package uk.codery.jspec.formatter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import lombok.extern.slf4j.Slf4j;
import uk.codery.jspec.result.EvaluationOutcome;

/**
 * Formats evaluation results as YAML.
 *
 * <p>Produces YAML output that is:
 * <ul>
 *   <li>Human-readable and easy to understand</li>
 *   <li>Compatible with YAML parsers</li>
 *   <li>Can be parsed back into Java objects</li>
 * </ul>
 *
 * <h2>Output Example</h2>
 * <pre>{@code
 * ---
 * specificationId: "order-validation"
 * results:
 * - criterion:
 *     id: "age-check"
 *     query:
 *       age:
 *         $gte: 18
 *   state: "MATCHED"
 *   missingPaths: []
 *   failureReason: null
 * summary:
 *   total: 1
 *   matched: 1
 *   notMatched: 0
 *   undetermined: 0
 *   fullyDetermined: true
 * }</pre>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Basic YAML Output</h3>
 * <pre>{@code
 * ResultFormatter formatter = new YamlResultFormatter();
 * String yaml = formatter.format(outcome);
 * System.out.println(yaml);
 * }</pre>
 *
 * <h3>Custom ObjectMapper</h3>
 * <pre>{@code
 * YAMLFactory yamlFactory = new YAMLFactory()
 *     .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
 * ObjectMapper mapper = new ObjectMapper(yamlFactory);
 * ResultFormatter formatter = new YamlResultFormatter(mapper);
 * String yaml = formatter.format(outcome);
 * }</pre>
 *
 * @param objectMapper -- GETTER --
 *                     Returns the underlying ObjectMapper.
 * @since 0.2.0
 */
@Slf4j
public record YamlResultFormatter(ObjectMapper objectMapper) implements ResultFormatter {

    /**
     * Creates a YAML formatter with default configuration.
     *
     * <p>Uses Jackson's YAMLFactory with:
     * <ul>
     *   <li>Minimal quotes (only when necessary)</li>
     *   <li>Document start marker (---) enabled</li>
     *   <li>Literal block scalars for multi-line strings</li>
     * </ul>
     */
    public YamlResultFormatter() {
        this(new ObjectMapper(new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                .enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE)));
    }

    @Override
    public String format(EvaluationOutcome outcome) {
        try {
            log.debug("Formatting evaluation outcome as YAML");
            return objectMapper.writeValueAsString(outcome);
        } catch (JsonProcessingException e) {
            log.error("Failed to format evaluation outcome as YAML", e);
            throw new FormatterException("Failed to format evaluation outcome as YAML", e);
        }
    }

    @Override
    public String formatType() {
        return "yaml";
    }

}
