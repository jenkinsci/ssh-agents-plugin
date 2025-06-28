/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sshbuildagents.ssh.mina.verifiers;

import org.apache.sshd.client.keyverifier.ServerKeyVerifier;

/**
 * A server key verifier that accepts all server keys without verification. This is not recommended
 * for production use as it does not provide any security. Use with caution, primarily for testing
 * purposes.
 *
 */
public class AcceptAllServerKeyVerifier extends KeyVerifier {
    @Override
    public ServerKeyVerifier getServerKeyVerifier() {
        return org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier.INSTANCE;
    }
}
