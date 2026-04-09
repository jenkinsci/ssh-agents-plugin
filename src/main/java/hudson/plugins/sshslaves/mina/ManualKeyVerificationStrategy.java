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
import hudson.slaves.SlaveComputer;
import hudson.util.FormValidation;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.sshd.client.keyverifier.RejectAllServerKeyVerifier;
import org.apache.sshd.client.keyverifier.RequiredServerKeyVerifier;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Verification strategy that validates the server host key against a manually provided public key.
 *
 * <p>The key should be in authorized_keys format (e.g., "ssh-rsa AAAA... comment").
 * If the key type is known, it will be used to set preferred algorithms during negotiation.
 */
public class ManualKeyVerificationStrategy extends MinaServerKeyVerificationStrategy {

    private static final Logger LOGGER = Logger.getLogger(ManualKeyVerificationStrategy.class.getName());

    private final String key;
    private transient AuthorizedKeyEntry entry;

    @DataBoundConstructor
    public ManualKeyVerificationStrategy(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    private AuthorizedKeyEntry getEntry() {
        if (entry == null) {
            entry = AuthorizedKeyEntry.parseAuthorizedKeyEntry(key);
        }
        return entry;
    }

    @Override
    @NonNull
    public ServerKeyVerifier createVerifier(SlaveComputer computer, String host) {
        AuthorizedKeyEntry keyEntry = getEntry();
        if (keyEntry != null) {
            try {
                PublicKey publicKey = keyEntry.resolvePublicKey(null, PublicKeyEntryResolver.IGNORING);
                if (publicKey != null) {
                    return new RequiredServerKeyVerifier(publicKey);
                }
                LOGGER.log(
                        Level.FINE,
                        () -> "Could not resolve public key from the configured server key, all keys are rejected.");
            } catch (IOException | GeneralSecurityException e) {
                LOGGER.log(Level.FINE, "Error resolving the configured server key, all keys are rejected.", e);
            }
        } else {
            LOGGER.log(Level.FINE, () -> "No server key configured, all keys are rejected.");
        }
        return RejectAllServerKeyVerifier.INSTANCE;
    }

    @Override
    @NonNull
    public List<String> getPreferredKeyAlgorithms() {
        AuthorizedKeyEntry keyEntry = getEntry();
        if (keyEntry != null) {
            String keyType = keyEntry.getKeyType();
            if (keyType != null && !keyType.isEmpty()) {
                LOGGER.log(Level.FINE, () -> "Configured host key type: " + keyType);
                return Collections.singletonList(keyType);
            }
        }
        return Collections.emptyList();
    }

    @Extension
    @Symbol("minaManualKey")
    public static class DescriptorImpl extends MinaServerKeyVerificationStrategyDescriptor {

        @Override
        @NonNull
        public String getDisplayName() {
            return Messages.ManualKeyVerificationStrategy_DisplayName();
        }

        @SuppressWarnings("unused")
        public FormValidation doCheckKey(@QueryParameter String value) {
            try {
                if (AuthorizedKeyEntry.parseAuthorizedKeyEntry(value) == null) {
                    return FormValidation.error("No valid key recovered");
                }
            } catch (IllegalArgumentException e) {
                return FormValidation.error(e.getMessage());
            }
            return FormValidation.ok();
        }
    }
}
