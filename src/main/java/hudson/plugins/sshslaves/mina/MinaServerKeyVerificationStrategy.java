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
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.slaves.SlaveComputer;
import java.util.Collections;
import java.util.List;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;

/**
 * Abstract base for Mina SSHD host key verification strategies.
 *
 * <p>Each implementation provides a {@link ServerKeyVerifier} for use during SSH connection
 * establishment. Implementations may optionally specify preferred key algorithms to influence
 * the SSH handshake negotiation.
 */
public abstract class MinaServerKeyVerificationStrategy
        extends AbstractDescribableImpl<MinaServerKeyVerificationStrategy> {

    /**
     * Creates a {@link ServerKeyVerifier} for the given computer.
     *
     * @param computer the slave computer being connected to
     * @param host the SSH host being connected to
     * @return a verifier to use during SSH handshake
     */
    @NonNull
    public abstract ServerKeyVerifier createVerifier(SlaveComputer computer, String host);

    /**
     * Returns the preferred host key algorithms for this strategy.
     *
     * <p>When non-empty, the SSH client will reorder the algorithm negotiation to
     * prefer the specified algorithms. This is useful when a specific host key type
     * is required (e.g., when manually providing an ED25519 key).
     *
     * @return list of algorithm names (e.g., "ssh-ed25519", "ssh-rsa"), or empty list
     */
    @NonNull
    public List<String> getPreferredKeyAlgorithms() {
        return Collections.emptyList();
    }

    /**
     * Descriptor base for Mina server key verification strategies.
     */
    public abstract static class MinaServerKeyVerificationStrategyDescriptor
            extends Descriptor<MinaServerKeyVerificationStrategy> {}
}
