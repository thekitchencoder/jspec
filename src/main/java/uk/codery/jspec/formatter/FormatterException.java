package uk.codery.jspec.formatter;

/**
 * Exception thrown when result formatting fails.
 *
 * <p>This exception wraps underlying formatting errors (e.g., JSON serialization
 * failures) to provide a consistent exception type for all formatters.
 *
 * <h2>Common Causes</h2>
 * <ul>
 *   <li>Jackson serialization errors</li>
 *   <li>Invalid result structure</li>
 *   <li>I/O errors during formatting</li>
 * </ul>
 *
 * @since 0.2.0
 */
public class FormatterException extends RuntimeException {

    /**
     * Creates a new formatter exception with a message.
     *
     * @param message the error message
     */
    public FormatterException(String message) {
        super(message);
    }

    /**
     * Creates a new formatter exception with a message and cause.
     *
     * @param message the error message
     * @param cause the underlying cause
     */
    public FormatterException(String message, Throwable cause) {
        super(message, cause);
    }
}
