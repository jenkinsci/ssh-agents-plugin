/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sshbuildagents.ssh.mina.verifiers;

import org.apache.sshd.client.keyverifier.ServerKeyVerifier;

/**
 * A server key verifier that uses the known hosts file for verification.
 *
 */
public class KnowHostServerKeyVerifier extends KeyVerifier {
    @Override
    public ServerKeyVerifier getServerKeyVerifier() {
        return new org.apache.sshd.client.keyverifier.DefaultKnownHostsServerKeyVerifier(null);
    }
}
