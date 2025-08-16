/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: MIT
 */
package io.jenkins.plugins.sshbuildagents.ssh;

import java.io.File;

/**
 * Class to manage known hosts for SSH connections. It provides methods to verify host keys and
 * manage known hosts files. TODO Implement a proper host key verification mechanism.
 *
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
