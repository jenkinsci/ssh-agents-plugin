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

import com.trilead.ssh2.Connection;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;

import java.io.IOException;

/**
 * A method for verifying the host key provided by the remote host during the
 * initiation of each connection.
 * 
 * @author Michael Clarke
 * @since 1.13
 */
public abstract class SshHostKeyVerificationStrategy implements Describable<SshHostKeyVerificationStrategy> {

    @Override
    public SshHostKeyVerificationStrategyDescriptor getDescriptor() {
        return (SshHostKeyVerificationStrategyDescriptor)Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    /**
     * Check if the given key is valid for the host identifier.
     * @param computer the computer this connection is being initiated for
     * @param hostKey the key that was transmitted by the remote host for the current connection. This is the key
     *                that should be checked to see if we trust it by the current verifier.
     * @param listener the connection listener to write any output log to
     * @return whether the provided HostKey is trusted and the current connection can therefore continue.
     * @since 1.12
     */
    public abstract boolean verify(SlaveComputer computer, HostKey hostKey, TaskListener listener) throws Exception;

    /**
     * Provides a list of preferred key algorithms for this strategy and computer.
     * @return a list of algorithms; empty or null lists will be ignored
     * @see Connection#setServerHostKeyAlgorithms
     */
    @CheckForNull
    public String[] getPreferredKeyAlgorithms(SlaveComputer computer) throws IOException {
        return TrileadVersionSupportManager.getTrileadSupport().getSupportedAlgorithms();
    }
    
    public static abstract class SshHostKeyVerificationStrategyDescriptor extends Descriptor<SshHostKeyVerificationStrategy> {
        
    }
    

    
}
