package hudson.plugins.sshslaves.verifiers;

import com.trilead.ssh2.signature.DSASHA1Verify;
import com.trilead.ssh2.signature.RSASHA1Verify;
import hudson.plugins.sshslaves.Messages;

import java.io.IOException;

/**
 * @author Michael Clarke
 * @since 1.18
 */
final class TrileadVersionSupportManager {

    static TrileadVersionSupport getTrileadSupport() {
        try {
            Thread.currentThread().getContextClassLoader().loadClass("com.trilead.ssh2.signature.KeyAlgorithmManager");
            return createaVersion9Instance();
        } catch (ReflectiveOperationException e) {
            // KeyAlgorithmManager doesn't exist, fall back to legacy trilead handler
            return new LegacyTrileadVersionSupport();
        }
    }

    private static TrileadVersionSupport createaVersion9Instance() {
        try {
            return (TrileadVersionSupport) Class.forName("hudson.plugins.sshslaves.verifiers.JenkinsTrilead9VersionSupport").newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Could not create Trilead support class", e);
        }

    }

    public abstract static class TrileadVersionSupport {

        /*package*/ TrileadVersionSupport() {
            super();
        }

        public abstract String[] getSupportedAlgorithms();

        public abstract HostKey parseKey(String algorithm, byte[] keyValue) throws KeyParseException;
    }

    private static class LegacyTrileadVersionSupport extends TrileadVersionSupport {

        @Override
        public String[] getSupportedAlgorithms() {
            return new String[]{"ssh-rsa", "ssh-dss"};
        }

        @Override
        public HostKey parseKey(String algorithm, byte[] keyValue) throws KeyParseException {
            try {
                if ("ssh-rsa".equals(algorithm)) {
                    RSASHA1Verify.decodeSSHRSAPublicKey(keyValue);
                } else if ("ssh-dss".equals(algorithm)) {
                    DSASHA1Verify.decodeSSHDSAPublicKey(keyValue);
                } else {
                    throw new KeyParseException("Key algorithm should be one of ssh-rsa or ssh-dss");
                }
            } catch (IOException | StringIndexOutOfBoundsException ex) {
                throw new KeyParseException(Messages.ManualKeyProvidedHostKeyVerifier_KeyValueDoesNotParse(algorithm), ex);
            }

            return new HostKey(algorithm, keyValue);
        }
    }

}
