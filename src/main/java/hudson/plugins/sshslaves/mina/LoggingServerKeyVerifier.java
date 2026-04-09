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

import hudson.model.TaskListener;
import java.net.SocketAddress;
import java.security.PublicKey;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.security.FIPS140;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.KeyUtils;

/**
 * Decorator that wraps a {@link ServerKeyVerifier} to log key fingerprints and verification
 * results to both the Jenkins task listener and Java logging.
 */
class LoggingServerKeyVerifier implements ServerKeyVerifier {

    private static final Logger LOGGER = Logger.getLogger(LoggingServerKeyVerifier.class.getName());

    private final ServerKeyVerifier delegate;
    private final TaskListener listener;

    LoggingServerKeyVerifier(ServerKeyVerifier delegate, TaskListener listener) {
        this.delegate = delegate;
        this.listener = listener;
    }

    @Override
    public boolean verifyServerKey(ClientSession clientSession, SocketAddress remoteAddress, PublicKey serverKey) {
        String kind;

        if (serverKey instanceof ECPublicKey) {
            kind = "ECDSA";
        } else if (serverKey instanceof RSAPublicKey) {
            kind = "RSA";
        } else if (serverKey instanceof DSAPublicKey) {
            kind = "DSA";
        } else {
            if (FIPS140.useCompliantAlgorithms()) {
                listener.getLogger().format("[SSH Mina] Error unknown server host key: %s%n", serverKey);
                return false;
            }
            if (!"net.i2p.crypto.eddsa.EdDSAPublicKey"
                    .equals(serverKey.getClass().getName())) {
                listener.getLogger().format("[SSH Mina] Warning unknown server host key type: %s%n", serverKey);
            }
            kind = serverKey.getAlgorithm();
        }

        LOGGER.log(
                Level.FINE,
                () -> "use kind " + kind + " for host " + clientSession.getRemoteAddress() + " publicKey: "
                        + serverKey);

        listener.getLogger().format("[SSH Mina] Verifying server host key...%n");
        listener.getLogger().format("[SSH Mina] %s key fingerprint is %s%n", kind, KeyUtils.getFingerPrint(serverKey));

        boolean result = delegate.verifyServerKey(clientSession, remoteAddress, serverKey);
        if (result) {
            listener.getLogger().format("[SSH Mina] Server host key verified%n");
        } else {
            listener.getLogger().format("[SSH Mina] Server host key rejected%n");
        }

        LOGGER.log(
                Level.FINE,
                () -> "verifier " + delegate.getClass().getName() + " return " + result + " for host "
                        + clientSession.getRemoteAddress() + " publicKey: " + serverKey);

        return result;
    }
}
