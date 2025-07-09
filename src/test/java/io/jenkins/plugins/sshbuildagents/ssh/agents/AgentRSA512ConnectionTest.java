/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: MIT
 */
package io.jenkins.plugins.sshbuildagents.ssh.agents;

import static hudson.plugins.sshslaves.tags.TestTags.AGENT_SSH_TEST;
import static hudson.plugins.sshslaves.tags.TestTags.SSH_KEX_TEST;

import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * Connect to a remote SSH Agent
 *
 */
@Tag(AGENT_SSH_TEST)
@Tag(SSH_KEX_TEST)
// FIXME verify log output some messages from the verifier are printed in the Jenkins Controller log
public class AgentRSA512ConnectionTest extends AgentConnectionBaseTest {
    public static final String SSH_AGENT_NAME = "ssh-agent-rsa512";
    public static final String SSH_KEY_PATH = "ssh/rsa-512-key";
    public static final String SSH_KEY_PUB_PATH = "ssh/rsa-512-key.pub";

    @SuppressWarnings("resource")
    @Container
    private static final GenericContainer<?> agentContainer = new GenericContainer<>(
                    newImageFromDockerfile(SSH_AGENT_NAME, SSH_KEY_PATH, SSH_KEY_PUB_PATH))
            .withExposedPorts(SSH_PORT);

    @Override
    protected String getAgentName() {
        return SSH_AGENT_NAME;
    }

    @Override
    protected GenericContainer<?> getAgentContainer() {
        return agentContainer;
    }

    @Override
    protected String getAgentSshKeyPath() {
        return SSH_AGENT_NAME + "/" + SSH_KEY_PATH;
    }
}
