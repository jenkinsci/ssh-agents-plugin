/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: MIT
 */
package io.jenkins.plugins.sshbuildagents.ssh;

import java.io.IOException;

/**
 * Class to manage key algorithms for SSH connections.
 *
 */
public class KeyAlgorithm {
    public String getKeyFormat() {
        return "";
    }

    public void decodePublicKey(byte[] keyValue) throws IOException {}
}
