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
package io.jenkins.plugins.sshbuildagents.ssh;

import java.io.File;

/**
 * Class to manage known hosts for SSH connections. It provides methods to verify host keys and
 * manage known hosts files. TODO Implement a proper host key verification mechanism.
 *
 * @author Ivan Fernandez Calvo
 */
public class KnownHosts {
    public static final int HOSTKEY_IS_OK = 0;
    public static final int HOSTKEY_IS_NEW = 1;

    public KnownHosts(File knownHostsFile) {}

    public static String createHexFingerprint(String algorithm, byte[] key) {
        return "";
    }

    public int verifyHostkey(String host, String algorithm, byte[] key) {
        return 1;
    }

    public String[] getPreferredServerHostkeyAlgorithmOrder(String host) {
        return new String[0];
    }
}
