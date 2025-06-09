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
import org.apache.sshd.common.util.security.eddsa.Ed25519PEMResourceKeyParser;

/**
 * Class to parse private keys from various formats using Apache Mina SSHD library.
 *
 * @author Ivan Fernandez Calvo
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
