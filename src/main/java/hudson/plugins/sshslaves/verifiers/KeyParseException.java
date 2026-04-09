package hudson.plugins.sshslaves.verifiers;

/**
 * @author Michael Clarke
 * @since 1.18
 * @deprecated Trilead-specific exception. Use {@link hudson.plugins.sshslaves.mina.MinaSSHLauncher} instead.
 */
@Deprecated
public class KeyParseException extends Exception {

    public KeyParseException(String message) {
        super(message);
    }

    public KeyParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
