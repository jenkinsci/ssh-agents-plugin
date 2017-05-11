/*
 * The MIT License
 *
 * Copyright (c) 2016, Michael Clarke
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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
 * user that configured this strategy. This would be equivalent of someone manually setting a
 * value in their known hosts file before attempting an SSH connection on a Unix/Linux machine.
 * @author Michael Clarke
 * @since 1.13
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
                throw new IllegalArgumentException(Messages.ManualKeyProvidedHostKeyVerifier_UnknownKeyAlgorithm());
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
