/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: MIT
 */
package io.jenkins.plugins.sshbuildagents.ssh.mina;

import io.jenkins.plugins.sshbuildagents.ssh.KeyAlgorithm;
import io.jenkins.plugins.sshbuildagents.ssh.KeyAlgorithmManager;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of KeyAlgorithmManager that provides a list of supported key algorithms. TODO
 * Implement a proper key algorithm manager that returns supported algorithms.
 *
 */
public class KeyAlgorithmManagerImpl implements KeyAlgorithmManager {
    @Override
    public List<KeyAlgorithm> getSupportedAlgorithms() {
        return Collections.emptyList();
    }
}
