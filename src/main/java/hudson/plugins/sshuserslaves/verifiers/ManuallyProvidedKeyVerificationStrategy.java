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
package hudson.plugins.sshuserslaves.verifiers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;


import hudson.Extension;
import hudson.model.TaskListener;
import hudson.plugins.sshuserslaves.Messages;
import hudson.plugins.sshuserslaves.SSHLauncher;
import hudson.remoting.Base64;
import hudson.slaves.SlaveComputer;
import hudson.util.FormValidation;
import java.util.Collections;

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
        try {
            this.key = parseKey(key);
        } catch (KeyParseException e) {
            throw new IllegalArgumentException("Invalid key: " + e.getMessage(), e);
        }
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

    @Override
    public String[] getPreferredKeyAlgorithms(SlaveComputer computer) throws IOException {
        String[] unsortedAlgorithms = super.getPreferredKeyAlgorithms(computer);
        List<String> sortedAlgorithms = new ArrayList<>(unsortedAlgorithms != null ? Arrays.asList(unsortedAlgorithms) : Collections.emptyList());

        sortedAlgorithms.remove(key.getAlgorithm());
        sortedAlgorithms.add(0, key.getAlgorithm());

        return sortedAlgorithms.toArray(new String[0]);
    }
    
    private static HostKey parseKey(String key) throws KeyParseException {
        if (!key.contains(" ")) {
            throw new IllegalArgumentException(Messages.ManualKeyProvidedHostKeyVerifier_TwoPartKey());
        }
        StringTokenizer tokenizer = new StringTokenizer(key, " ");
        String algorithm = tokenizer.nextToken();
        byte[] keyValue = Base64.decode(tokenizer.nextToken());
        if (null == keyValue) {
            throw new KeyParseException(Messages.ManualKeyProvidedHostKeyVerifier_Base64EncodedKeyValueRequired());
        }
        
        return TrileadVersionSupportManager.getTrileadSupport().parseKey(algorithm, keyValue);
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
            } catch (KeyParseException ex) {
                return FormValidation.error(ex.getMessage());
            }
        }
        
    }

}
