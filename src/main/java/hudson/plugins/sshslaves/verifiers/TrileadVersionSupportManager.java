package hudson.plugins.sshslaves.verifiers;

import com.trilead.ssh2.signature.DSASHA1Verify;
import com.trilead.ssh2.signature.RSASHA1Verify;
import hudson.plugins.sshslaves.Messages;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Michael Clarke
 * @since 1.18
 */
@Restricted(NoExternalUse.class)
final class TrileadVersionSupportManager {

    private static final Logger LOGGER = Logger.getLogger(TrileadVersionSupportManager.class.getName());

    static TrileadVersionSupport getTrileadSupport() {
        try {
            if (isAfterTrilead8()) {
                return createVersion9Instance();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not create Trilead support class. Using legacy Trilead features", e);
        }
        // We're on an old version of Triilead or couldn't create a new handler, fall back to legacy trilead handler
        return new LegacyTrileadVersionSupport();
    }

    private static boolean isAfterTrilead8() {
        try {
            Thread.currentThread().getContextClassLoader().loadClass("com.trilead.ssh2.signature.KeyAlgorithmManager");
        } catch (ClassNotFoundException ex) {
            return false;
        }
        return true;
    }

    private static TrileadVersionSupport createVersion9Instance() throws ReflectiveOperationException {
        return (TrileadVersionSupport) Thread.currentThread().getContextClassLoader()
                .loadClass("hudson.plugins.sshslaves.verifiers.JenkinsTrilead9VersionSupport").newInstance();

    }

    public abstract static class TrileadVersionSupport {

        @Restricted(NoExternalUse.class)
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
