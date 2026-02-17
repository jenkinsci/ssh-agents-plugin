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
import hudson.model.Slave;
import hudson.slaves.EphemeralNode;
import hudson.slaves.SlaveComputer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.SocketAddress;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.model.Nodes;
import org.apache.commons.io.FileUtils;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntryDecoder;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Trust-on-first-use (TOFU) verification strategy.
 *
 * <p>On the first connection to a new host, the server's host key is stored. On subsequent
 * connections, the stored key is compared with the presented key.
 *
 * <p>When {@link #manualVerification} is {@code true}, the first connection will be rejected
 * and the key must be manually approved via the Jenkins UI before the agent can connect.
 *
 * <p>Trusted keys are stored per-node in {@code $JENKINS_HOME/nodes/<name>/authorized_key}.
 */
public class TrustOnFirstUseVerificationStrategy extends MinaServerKeyVerificationStrategy {

    private static final Logger LOGGER = Logger.getLogger(TrustOnFirstUseVerificationStrategy.class.getName());

    private final boolean manualVerification;

    @DataBoundConstructor
    public TrustOnFirstUseVerificationStrategy(boolean manualVerification) {
        this.manualVerification = manualVerification;
    }

    public boolean isManualVerification() {
        return manualVerification;
    }

    @Override
    @NonNull
    public ServerKeyVerifier createVerifier(SlaveComputer computer, String host) {
        AuthorizedKeyEntry storedEntry;
        try {
            storedEntry = loadStoredKey(computer);
        } catch (IOException e) {
            storedEntry = null;
        }

        if (storedEntry != null) {
            // We have a stored key - verify against it
            try {
                final PublicKey expected = storedEntry.resolvePublicKey(null, PublicKeyEntryResolver.IGNORING);
                return (ClientSession clientSession, SocketAddress remoteAddress, PublicKey serverKey) -> {
                    boolean result = KeyUtils.compareKeys(expected, serverKey);
                    LOGGER.log(
                            Level.FINE,
                            () -> "Comparing expected: " + expected + ", serverKey: " + serverKey + ", result: "
                                    + result);
                    return result;
                };
            } catch (IOException | GeneralSecurityException e) {
                LOGGER.log(Level.FINE, "Error resolving stored key for verification", e);
            }
        }

        // No stored key - trust on first use (or require manual verification)
        return (ClientSession clientSession, SocketAddress remoteAddress, PublicKey serverKey) -> {
            @SuppressWarnings("unchecked")
            PublicKeyEntryDecoder<PublicKey, ?> decoder =
                    (PublicKeyEntryDecoder<PublicKey, ?>) KeyUtils.getPublicKeyEntryDecoder(serverKey);
            try (ByteArrayOutputStream s = new ByteArrayOutputStream(Byte.MAX_VALUE)) {
                AuthorizedKeyEntry newEntry = new AuthorizedKeyEntry();
                newEntry.setKeyType(decoder.encodePublicKey(s, serverKey));
                newEntry.setKeyData(s.toByteArray());

                if (manualVerification) {
                    LOGGER.log(Level.FINE, () -> "Manual verification required, rejecting first-time key");
                    return false;
                }

                // Auto-trust: store the key
                storeKey(computer, newEntry);
                return true;
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error saving server key", e);
                return false;
            }
        };
    }

    private AuthorizedKeyEntry loadStoredKey(SlaveComputer computer) throws IOException {
        File nodesDir = getNodesDir();
        Slave node = computer.getNode();
        if (node == null || node instanceof EphemeralNode || nodesDir == null) {
            return null;
        }
        File authorizedKeyFile = new File(new File(nodesDir, node.getNodeName()), "authorized_key");
        if (authorizedKeyFile.isFile()) {
            return AuthorizedKeyEntry.parseAuthorizedKeyEntry(FileUtils.readFileToString(authorizedKeyFile, "UTF-8"));
        }
        return null;
    }

    private void storeKey(SlaveComputer computer, AuthorizedKeyEntry entry) {
        File nodesDir = getNodesDir();
        Slave node = computer.getNode();
        if (node == null || node instanceof EphemeralNode || nodesDir == null) {
            return;
        }
        File authorizedKeyFile = new File(new File(nodesDir, node.getNodeName()), "authorized_key");
        if (entry == null) {
            FileUtils.deleteQuietly(authorizedKeyFile);
        } else {
            StringBuilder buf = new StringBuilder();
            try {
                entry.appendPublicKey(null, buf, PublicKeyEntryResolver.IGNORING);
                FileUtils.write(authorizedKeyFile, buf, "UTF-8");
            } catch (IOException | GeneralSecurityException e) {
                FileUtils.deleteQuietly(authorizedKeyFile);
            }
        }
    }

    private static File getNodesDir() {
        Jenkins jenkins = Jenkins.get();
        try {
            Method getNodesDir = Nodes.class.getDeclaredMethod("getNodesDir");
            getNodesDir.setAccessible(true);
            Method getNodesObject = Jenkins.class.getDeclaredMethod("getNodesObject");
            getNodesObject.setAccessible(true);
            Object nodes = getNodesObject.invoke(jenkins);
            return (File) getNodesDir.invoke(nodes);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            File nodesDir = new File(jenkins.getRootDir(), "nodes");
            if (!nodesDir.isDirectory() && !nodesDir.mkdirs()) {
                return null;
            }
            return nodesDir;
        }
    }

    @Extension
    @Symbol("minaTrustFirstUse")
    public static class DescriptorImpl extends MinaServerKeyVerificationStrategyDescriptor {

        @Override
        @NonNull
        public String getDisplayName() {
            return Messages.TrustOnFirstUseVerificationStrategy_DisplayName();
        }
    }
}
