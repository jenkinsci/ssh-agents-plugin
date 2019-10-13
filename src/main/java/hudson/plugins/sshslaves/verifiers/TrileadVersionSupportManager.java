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
 * An abstraction layer to allow handling of feature changes (e.g. new key types) between different Trilead versions.
 *
 * @author Michael Clarke
 * @since 1.18
 */
@Restricted(NoExternalUse.class)
final class TrileadVersionSupportManager {

  private static final Logger LOGGER = Logger.getLogger(TrileadVersionSupportManager.class.getName());

  /**
   * Craetes an instance of TrileadVersionSupport that can provide functionality relevant to the version of Trilead
   * available in the current executing instance of Jenkins.
   *
   * @return an instance of TrileadVersionSupport that provides functionality relevant for the version of Trilead
   * currently on the classpath
   */
  static TrileadVersionSupport getTrileadSupport() {
    try {
      if (isAfterTrilead8()) {
        return createVersion9Instance();
      }
    } catch (Exception | LinkageError e) {
      LOGGER.log(Level.WARNING, "Could not create Trilead support class. Using legacy Trilead features", e);
    }
    // We're on an old version of Triilead or couldn't create a new handler, fall back to legacy trilead handler
    return new LegacyTrileadVersionSupport();
  }

  private static boolean isAfterTrilead8() {
    try {
      Class.forName("com.trilead.ssh2.signature.KeyAlgorithmManager");
    } catch (ClassNotFoundException ex) {
      return false;
    }
    return true;
  }

  private static TrileadVersionSupport createVersion9Instance() throws ReflectiveOperationException {
    return (TrileadVersionSupport) TrileadVersionSupportManager.class.getClassLoader()
      .loadClass("hudson.plugins.sshslaves.verifiers.JenkinsTrilead9VersionSupport").newInstance();

  }

  public abstract static class TrileadVersionSupport {

    @Restricted(NoExternalUse.class)
      /*package*/ TrileadVersionSupport() {
      super();
    }

    /**
     * Returns an array of all Key algorithms supported by Yrilead, e.g. ssh-rsa, ssh-dsa, ssh-eds25519
     *
     * @return an array containing all the key algorithms the version of Trilead in use can support.
     */
    public abstract String[] getSupportedAlgorithms();

    /**
     * Parses a raw key into a {@link HostKey} for later storage or comparison.
     *
     * @param algorithm the algorithm the key has been generated with, e.h. ssh-rsa, ssh-dss, ssh-ed25519
     * @param keyValue  the value of the key, typically encoded in PEM format.
     * @return the input key in a format that can be compared to other keys
     * @throws KeyParseException on any failure parsing the key, such as an unknown algorithm or invalid keyValue
     */
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
