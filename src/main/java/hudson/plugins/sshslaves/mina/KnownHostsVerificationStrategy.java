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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.sshd.client.keyverifier.DefaultKnownHostsServerKeyVerifier;
import org.apache.sshd.client.keyverifier.RejectAllServerKeyVerifier;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Verification strategy that validates server host keys against the user's ~/.ssh/known_hosts file.
 *
 * <p>Only hosts that are listed in the known_hosts file will be accepted. All others are rejected.
 */
public class KnownHostsVerificationStrategy extends MinaServerKeyVerificationStrategy {

    private static final Logger LOGGER = Logger.getLogger(KnownHostsVerificationStrategy.class.getName());

    @DataBoundConstructor
    public KnownHostsVerificationStrategy() {}

    @Override
    @NonNull
    public ServerKeyVerifier createVerifier(SlaveComputer computer, String host) {
        ServerKeyVerifier verifier = new DefaultKnownHostsServerKeyVerifier(RejectAllServerKeyVerifier.INSTANCE);
        LOGGER.log(Level.FINE, () -> "Created known_hosts verifier: " + verifier);
        return verifier;
    }

    @Extension
    @Symbol("minaKnownHosts")
    public static class DescriptorImpl extends MinaServerKeyVerificationStrategyDescriptor {

        @Override
        @NonNull
        public String getDisplayName() {
            return Messages.KnownHostsVerificationStrategy_DisplayName();
        }
    }
}
