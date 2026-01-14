/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: MIT
 */
package io.jenkins.plugins.sshbuildagents.ssh;

import java.util.List;

/**
 * Interface to manage supported key algorithms for SSH connections.
 *
 */
public interface KeyAlgorithmManager {

    List<KeyAlgorithm> getSupportedAlgorithms();
}
