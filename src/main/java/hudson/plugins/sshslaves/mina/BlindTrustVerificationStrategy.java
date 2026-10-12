/*
 * The MIT License
 *
 * Copyright (c) 2004-, all the contributors
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
package hudson.plugins.sshslaves.mina;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.slaves.SlaveComputer;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Verification strategy that accepts all server host keys without verification.
 *
 * <p>This is the least secure option and should only be used in trusted environments.
 */
public class BlindTrustVerificationStrategy extends MinaServerKeyVerificationStrategy {

    @DataBoundConstructor
    public BlindTrustVerificationStrategy() {}

    @Override
    @NonNull
    public ServerKeyVerifier createVerifier(SlaveComputer computer, String host) {
        return AcceptAllServerKeyVerifier.INSTANCE;
    }

    @Extension
    @Symbol("minaBlindlyTrust")
    public static class DescriptorImpl extends MinaServerKeyVerificationStrategyDescriptor {

        @Override
        @NonNull
        public String getDisplayName() {
            return Messages.BlindTrustVerificationStrategy_DisplayName();
        }
    }
}
