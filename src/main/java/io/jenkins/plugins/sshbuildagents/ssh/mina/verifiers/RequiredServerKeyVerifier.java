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

package io.jenkins.plugins.sshbuildagents.ssh.mina.verifiers;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.PublicKey;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;

/**
 * A server key verifier that requires a specific public key for authentication.
 *
 * @author Ivan Fernandez Calvo
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
