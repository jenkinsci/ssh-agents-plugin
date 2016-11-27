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

import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.plugins.sshslaves.verifiers.HostKeyManager.HostIdentifier;
import hudson.plugins.sshslaves.verifiers.HostKeyManager.HostKey;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;

/**
 * A method for verifying the host key provided by the remote host during the
 * initiation of each connection.
 * 
 * @author Michael Clarke
 * @since 1.12
 */
public abstract class HostKeyVerifier implements Describable<HostKeyVerifier> {

    @Override
    public HostKeyVerifierDescriptor getDescriptor() {
        return (HostKeyVerifierDescriptor)Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    /**
     * Check if the given key is valid for the host identifier.
     * @param computer the computer this connection is being initiated for
     * @param hostIdentifier the identifier for the host we're currently connecting to. This identifier will be the
     *                       same for any <tt>Computer</tt>s that have the same host-name/IP address and port number
     *                       so may not be unique for all slaves in a Jenkins cluster, should two services launch off
     *                       the same host and port.
     * @param hostKey the key that was transmitted by the remote host for the current connection. This is the key
     *                that should be checked to see if we trust it by the current verifier.
     * @param listener the connection listener to write any output log to
     * @return whether the provided HostKey is trusted and the current connection can therefore continue.
     * @since 1.12
     */
    public abstract boolean verify(SlaveComputer computer, HostIdentifier hostIdentifier, HostKey hostKey, TaskListener listener);
    
    public static abstract class HostKeyVerifierDescriptor extends Descriptor<HostKeyVerifier> {
        
    }
    

    
}
