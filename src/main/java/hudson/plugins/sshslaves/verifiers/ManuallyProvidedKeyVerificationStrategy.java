package hudson.plugins.sshslaves.verifiers;

import java.io.IOException;
import java.util.StringTokenizer;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.trilead.ssh2.signature.DSASHA1Verify;
import com.trilead.ssh2.signature.RSASHA1Verify;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.plugins.sshslaves.Messages;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.remoting.Base64;
import hudson.slaves.SlaveComputer;
import hudson.util.FormValidation;

/**
 * Checks a key provided by a remote hosts matches a key specified as being required by the
 * user that configured this strategy. This would be equivelnt of someone manually setting a
 * value in their known hosts file before attempting an SSH connection on a Unix/Linux machine.
 * @author Michael Clarke
 * @since 1.12
 */
public class ManuallyProvidedKeyVerificationStrategy extends SshHostKeyVerificationStrategy {

    private final HostKey key;
    
    @DataBoundConstructor
    public ManuallyProvidedKeyVerificationStrategy(String key) {
        super();
        this.key = parseKey(key);
    }
    
    public String getKey() {
        return key.getAlgorithm() + " " + Base64.encode(key.getKey());
    }
    
    public HostKey getParsedKey() {
        return key;
    }
    
    @Override
    public boolean verify(SlaveComputer computer, HostKey hostKey, TaskListener listener) throws Exception {
        if (key.equals(hostKey)) {
            listener.getLogger().println(Messages.ManualKeyProvidedHostKeyVerifier_KeyTrusted(SSHLauncher.getTimestamp()));
            return true;
        } else {
            listener.getLogger().println(Messages.ManualKeyProvidedHostKeyVerifier_KeyNotTrusted(SSHLauncher.getTimestamp()));
            return false;
        }
    }
    
    private static HostKey parseKey(String key) {
        if (!key.contains(" ")) {
            throw new IllegalArgumentException(Messages.ManualKeyProvidedHostKeyVerifier_TwoPartKey());
        }
        StringTokenizer tokenizer = new StringTokenizer(key, " ");
        String algorithm = tokenizer.nextToken();
        byte[] keyValue = Base64.decode(tokenizer.nextToken());
        if (null == keyValue) {
        throw new IllegalArgumentException(Messages.ManualKeyProvidedHostKeyVerifier_Base64EncodedKeyValueRequired());
        }
        
        try {
            if ("ssh-rsa".equals(algorithm)) {
                RSASHA1Verify.decodeSSHRSAPublicKey(keyValue);
            } else if ("ssh-dss".equals(algorithm)) {
                DSASHA1Verify.decodeSSHDSAPublicKey(keyValue);
            } else {
                throw new IllegalArgumentException("Key algorithm should be one of ssh-rsa or ssh-dss");
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException(Messages.ManualKeyProvidedHostKeyVerifier_KeyValueDoesNotParse(algorithm), ex);
        }  catch (StringIndexOutOfBoundsException ex) {
            // can happen in DSASHA1Verifier with certain values (from quick testing)
            throw new IllegalArgumentException(Messages.ManualKeyProvidedHostKeyVerifier_KeyValueDoesNotParse(algorithm), ex);
        }
        
        return new HostKey(algorithm, keyValue);
    }
    
    @Extension
    public static class ManuallyProvidedKeyVerificationStrategyDescriptor extends SshHostKeyVerificationStrategyDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.ManualKeyProvidedHostKeyVerifier_DisplayName();
        }
        
        public FormValidation doCheckKey(@QueryParameter String key) {
            try {
                ManuallyProvidedKeyVerificationStrategy.parseKey(key);
                return FormValidation.ok();
            } catch (IllegalArgumentException ex) {
                return FormValidation.error(ex.getMessage());
            }
        }
        
    }

}
