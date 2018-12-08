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

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.plugins.sshuserslaves.Messages;
import hudson.plugins.sshuserslaves.SSHLauncher;
import hudson.slaves.SlaveComputer;

/**
 * A verifier that performs no action on the host key, thereby allowing all connections. To
 * make it clear that no verification is being performed, a message is printed to connection
 * logs to indicate the key is not being checked and a man-in-the-middle attach may therefore
 * be possible against this connection.
 * @author Michael Clarke
 * @since 1.13
 */
public class NonVerifyingKeyVerificationStrategy extends SshHostKeyVerificationStrategy {

    @DataBoundConstructor
    public NonVerifyingKeyVerificationStrategy() {
        super();
    }
    
    @Override
    public boolean verify(SlaveComputer computer, HostKey hostKey, TaskListener listener) {
        listener.getLogger().println(Messages.NonVerifyingHostKeyVerifier_NoVerificationWarning(SSHLauncher.getTimestamp()));
        return true;
    }
    
    @Extension
    public static class NonVerifyingKeyVerificationStrategyDescriptor extends SshHostKeyVerificationStrategyDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.NonVerifyingHostKeyVerifier_DescriptorDisplayName();
        }
        
    }
    
}