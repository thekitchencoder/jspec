/**
 * Result formatters for converting evaluation outcomes to various output formats.
 *
 * <h2>Overview</h2>
 *
 * <p>This package provides formatters for serializing {@link uk.codery.jspec.result.EvaluationOutcome}
 * objects into different output formats:
 * <ul>
 *   <li><b>JSON:</b> Machine-readable structured format ({@link uk.codery.jspec.formatter.JsonResultFormatter})</li>
 *   <li><b>YAML:</b> Human-readable structured format ({@link uk.codery.jspec.formatter.YamlResultFormatter})</li>
 *   <li><b>Text:</b> Plain text human-readable format ({@link uk.codery.jspec.formatter.TextResultFormatter})</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>JSON Formatting</h3>
 * <pre>{@code
 * ResultFormatter formatter = new JsonResultFormatter(true);  // Pretty-print
 * String json = formatter.format(outcome);
 * System.out.println(json);
 * }</pre>
 *
 * <h3>YAML Formatting</h3>
 * <pre>{@code
 * ResultFormatter formatter = new YamlResultFormatter();
 * String yaml = formatter.format(outcome);
 * System.out.println(yaml);
 * }</pre>
 *
 * <h3>Text Formatting</h3>
 * <pre>{@code
 * ResultFormatter formatter = new TextResultFormatter(true);  // Verbose
 * String text = formatter.format(outcome);
 * System.out.println(text);
 * }</pre>
 *
 * <h3>Format Selection</h3>
 * <pre>{@code
 * ResultFormatter formatter = switch (format) {
 *     case "json" -> new JsonResultFormatter();
 *     case "yaml" -> new YamlResultFormatter();
 *     case "text" -> new TextResultFormatter();
 *     default -> throw new IllegalArgumentException("Unknown format: " + format);
 * };
 *
 * String output = formatter.format(outcome);
 * }</pre>
 *
 * <h2>Design</h2>
 *
 * <p>All formatters implement the {@link uk.codery.jspec.formatter.ResultFormatter}
 * interface, providing a consistent API for formatting evaluation outcomes.
 *
 * <p>Formatters are:
 * <ul>
 *   <li><b>Immutable:</b> Thread-safe and can be reused</li>
 *   <li><b>Configurable:</b> Support customization through constructors</li>
 *   <li><b>Error-handling:</b> Throw {@link uk.codery.jspec.formatter.FormatterException} on failure</li>
 * </ul>
 *
 * <h2>Integration with Spring</h2>
 *
 * <pre>{@code
 * @Configuration
 * public class FormatterConfig {
 *
 *     @Bean
 *     public ResultFormatter jsonFormatter() {
 *         return new JsonResultFormatter(true);
 *     }
 *
 *     @Bean
 *     public ResultFormatter yamlFormatter() {
 *         return new YamlResultFormatter();
 *     }
 *
 *     @Bean
 *     public ResultFormatter textFormatter() {
 *         return new TextResultFormatter(false);
 *     }
 * }
 * }</pre>
 *
 * @since 0.2.0
 */
package uk.codery.jspec.formatter;
