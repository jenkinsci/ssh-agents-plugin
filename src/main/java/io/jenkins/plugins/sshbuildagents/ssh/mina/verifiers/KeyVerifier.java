/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sshbuildagents.ssh.mina.verifiers;

import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;

/**
 * Abstract class for key verifiers used in SSH connections. This class provides a method to
 * retrieve the server key verifier. It is intended to be extended by specific key verifier
 * implementations.
 *
 */
public abstract class KeyVerifier implements Describable<KeyVerifier> {

    @Override
    public KeyVerifierDescriptor getDescriptor() {
        return (KeyVerifierDescriptor) Jenkins.get().getDescriptorOrDie(getClass());
    }

    public abstract ServerKeyVerifier getServerKeyVerifier();

    public abstract static class KeyVerifierDescriptor extends Descriptor<KeyVerifier> {}
}
