/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sshbuildagents.ssh.mina.verifiers;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.PublicKey;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;

/**
 * A server key verifier that requires a specific public key for authentication.
 *
 */
public class RequiredServerKeyVerifier extends KeyVerifier {
    private String publicKey;

    public RequiredServerKeyVerifier(@NonNull String publicKey) {
        this.publicKey = publicKey;
    }

    @Override
    public ServerKeyVerifier getServerKeyVerifier() {
        PublicKey requiredKey = null;
        return new org.apache.sshd.client.keyverifier.RequiredServerKeyVerifier(requiredKey);
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }
}
