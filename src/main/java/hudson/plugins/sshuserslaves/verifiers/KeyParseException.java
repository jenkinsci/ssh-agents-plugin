package hudson.plugins.sshuserslaves.verifiers;

/**
 * @author Michael Clarke
 * @since 1.18
 */
public class KeyParseException extends Exception {

    public KeyParseException(String message) {
        super(message);
    }

    public KeyParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
