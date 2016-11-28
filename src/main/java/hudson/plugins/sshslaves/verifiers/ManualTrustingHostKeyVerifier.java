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

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.plugins.sshslaves.Messages;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.plugins.sshslaves.verifiers.HostKeyManager.HostKey;
import hudson.slaves.SlaveComputer;

/**
 * A host key verifier that works in a similar way to host key verification on Unix/Linux:
 * requiring manual intervention if no key has previously been seen for this host, or if
 * the key provided by the remote host differs from the one currently saved in the known
 * hosts file. This manual verification is achieved through adding a {@link TrustHostKeyAction }
 * to the Computer the connection is being initiated for that can be actioned by a user with
 * the appropriate permission to add a new key or replace an existing key in the known hosts
 * database.
 * @author Michael Clarke
 * @since 1.12
 */
public class ManualTrustingHostKeyVerifier extends HostKeyVerifier {
    
    @DataBoundConstructor
    public ManualTrustingHostKeyVerifier() {
        super();
    }
    
    @Override
    public boolean verify(final SlaveComputer computer, HostKey hostKey, TaskListener listener) throws IOException {
        HostKeyManager hostManager = HostKeyManager.getInstance();
        
        HostKey existingHostKey = hostManager.getHostKey(computer);
        
        if (null == existingHostKey || !existingHostKey.equals(hostKey)) {
            listener.getLogger().println(Messages.ManualTrustingHostKeyVerifier_KeyNotTrusted(SSHLauncher.getTimestamp()));
            if (!hasExistingTrustAction(computer, hostKey)) {
                computer.addAction(new TrustHostKeyAction(computer, hostKey));
            }
            return false;
        }
        else {
            listener.getLogger().println(Messages.ManualTrustingHostKeyVerifier_KeyTrused(SSHLauncher.getTimestamp()));
            return true;
        }
    }
    
    private boolean hasExistingTrustAction(SlaveComputer computer, HostKey hostKey) {
        for (TrustHostKeyAction action : computer.getActions(TrustHostKeyAction.class)) {
            if (!action.isComplete() && action.getHostKey().equals(hostKey)) {
                return true;
            }
        }
        
        return false;
    }
    
    @Extension
    public static class ManualTrustingHostKeyVerifierDescriptor extends HostKeyVerifierDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.ManualTrustingHostKeyVerifier_DescriptorDisplayName();
        }
        
    }
    
}