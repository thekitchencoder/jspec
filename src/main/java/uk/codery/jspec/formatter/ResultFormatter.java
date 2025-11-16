package uk.codery.jspec.formatter;

import uk.codery.jspec.result.EvaluationOutcome;

/**
 * Formats evaluation results into various output formats.
 *
 * <p>Implementations support different output formats:
 * <ul>
 *   <li>JSON - Machine-readable structured format</li>
 *   <li>YAML - Human-readable structured format</li>
 *   <li>Text - Plain text human-readable format</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>JSON Format</h3>
 * <pre>{@code
 * ResultFormatter formatter = new JsonResultFormatter();
 * String json = formatter.format(outcome);
 * System.out.println(json);
 * }</pre>
 *
 * <h3>YAML Format</h3>
 * <pre>{@code
 * ResultFormatter formatter = new YamlResultFormatter();
 * String yaml = formatter.format(outcome);
 * System.out.println(yaml);
 * }</pre>
 *
 * <h3>Text Format</h3>
 * <pre>{@code
 * ResultFormatter formatter = new TextResultFormatter();
 * String text = formatter.format(outcome);
 * System.out.println(text);
 * }</pre>
 *
 * <h3>Pretty Printing</h3>
 * <pre>{@code
 * ResultFormatter formatter = new JsonResultFormatter(true);  // Pretty print
 * String prettyJson = formatter.format(outcome);
 * }</pre>
 *
 * @see JsonResultFormatter
 * @see YamlResultFormatter
 * @see TextResultFormatter
 * @since 0.2.0
 */
public interface ResultFormatter {

    /**
     * Formats an evaluation outcome into a string.
     *
     * @param outcome the evaluation outcome to format
     * @return formatted string representation
     * @throws FormatterException if formatting fails
     */
    String format(EvaluationOutcome outcome);

    /**
     * Returns the format type of this formatter.
     *
     * @return format type (e.g., "json", "yaml", "text")
     */
    String formatType();
}
