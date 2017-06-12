package hudson.plugins.sshslaves.verifiers;

import com.trilead.ssh2.signature.KeyAlgorithm;
import com.trilead.ssh2.signature.KeyAlgorithmManager;
import hudson.plugins.sshslaves.Messages;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Michael Clarke
 */
@Restricted(NoExternalUse.class)
class JenkinsTrilead9VersionSupport extends TrileadVersionSupportManager.TrileadVersionSupport {

    @Override
    public String[] getSupportedAlgorithms() {
        List<String> algorithms = new ArrayList<>();
        for (KeyAlgorithm<?, ?> algorithm : KeyAlgorithmManager.getSupportedAlgorithms()) {
            algorithms.add(algorithm.getKeyFormat());
        }
        return algorithms.toArray(new String[algorithms.size()]);
    }

    @Override
    public HostKey parseKey(String algorithm, byte[] keyValue) throws KeyParseException {
        for (KeyAlgorithm<?, ?> keyAlgorithm : KeyAlgorithmManager.getSupportedAlgorithms()) {
            try {
                if (keyAlgorithm.getKeyFormat().equals(algorithm)) {
                    keyAlgorithm.decodePublicKey(keyValue);
                    return new HostKey(algorithm, keyValue);
                }
            } catch (IOException ex) {
                throw new KeyParseException(Messages.ManualKeyProvidedHostKeyVerifier_KeyValueDoesNotParse(algorithm), ex);
            }
        }
        throw new KeyParseException("Unexpected key algorithm: " + algorithm);
    }
}
