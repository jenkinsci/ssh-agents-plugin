/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: MIT
 */
package io.jenkins.plugins.sshbuildagents.ssh.mina;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.config.keys.loader.AbstractKeyPairResourceParser;
import org.apache.sshd.common.config.keys.loader.KeyPairResourceParser;
import org.apache.sshd.common.config.keys.loader.openssh.OpenSSHKeyPairResourceParser;
import org.apache.sshd.common.config.keys.loader.pem.DSSPEMResourceKeyPairParser;
import org.apache.sshd.common.config.keys.loader.pem.ECDSAPEMResourceKeyPairParser;
import org.apache.sshd.common.config.keys.loader.pem.PKCS8PEMResourceKeyPairParser;
import org.apache.sshd.common.config.keys.loader.pem.RSAPEMResourceKeyPairParser;
import org.apache.sshd.common.util.security.bouncycastle.BouncyCastleKeyPairResourceParser;
// TODO Get rid of net.i2p.crypto.eddsa with using BC
import org.apache.sshd.common.util.security.eddsa.Ed25519PEMResourceKeyParser;

/**
 * Class to parse private keys from various formats using Apache Mina SSHD library.
 *
 */
public class PrivateKeyParser {
    List<KeyPairResourceParser> parsers = new ArrayList<>();

    public PrivateKeyParser() {
        parsers.add(new BouncyCastleKeyPairResourceParser());
        parsers.add(new DSSPEMResourceKeyPairParser());
        parsers.add(new ECDSAPEMResourceKeyPairParser());
        parsers.add(new PKCS8PEMResourceKeyPairParser());
        parsers.add(new RSAPEMResourceKeyPairParser());
        parsers.add(new Ed25519PEMResourceKeyParser());
        parsers.add(new OpenSSHKeyPairResourceParser());
    }

    /**
     * Parses a private key from a string representation.
     *
     * @param key The private key as a string.
     * @param passphrase The passphrase for the private key, if applicable.
     * @return A collection of KeyPair objects parsed from the key string.
     * @throws IOException If an I/O error occurs during parsing.
     * @throws GeneralSecurityException If a security error occurs during parsing.
     * @throws URISyntaxException If the key URI is malformed.
     */
    public Collection<KeyPair> parseKey(String key, String passphrase)
            throws IOException, GeneralSecurityException, URISyntaxException {
        List<String> lines = Arrays.asList(key.split("\n"));
        Collection<KeyPair> keys = Collections.emptyList();
        for (KeyPairResourceParser parser : parsers) {
            if (!(parser instanceof AbstractKeyPairResourceParser)) {
                continue;
            }
            FakeURI resourceKey = new FakeURI(key);
            if (parser.canExtractKeyPairs(resourceKey, lines)) {
                keys = parser.loadKeyPairs(null, resourceKey, FilePasswordProvider.of(passphrase), lines);
            }
        }
        return keys;
    }
}
