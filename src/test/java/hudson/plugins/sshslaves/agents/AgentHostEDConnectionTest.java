package hudson.plugins.sshslaves.agents;

import static hudson.plugins.sshslaves.tags.TestTags.AGENT_SSH_TEST;
import static hudson.plugins.sshslaves.tags.TestTags.SSH_HOST_KEY_TEST;

import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;

/**
 * Connect to a remote SSH Agent
 *
 * @author Kuisathaverat
 */
@Tag(AGENT_SSH_TEST)
@Tag(SSH_HOST_KEY_TEST)
class AgentHostEDConnectionTest extends AgentConnectionBaseTest {
    private static final String SSH_AGENT_NAME = "ssh-agent-host-ed";
    private static final String SSH_KEY_PATH = "ssh/rsa-key";
    private static final String SSH_KEY_PUB_PATH = "ssh/rsa-key.pub";

    @Container
    private static final GenericContainer<?> agentContainer = new GenericContainer<>(new ImageFromDockerfile(
                            SSH_AGENT_NAME, false)
                    .withFileFromClasspath(
                            SSH_AUTHORIZED_KEYS,
                            AGENTS_RESOURCES_PATH + "/" + SSH_AGENT_NAME + "/" + SSH_AUTHORIZED_KEYS)
                    .withFileFromClasspath(
                            SSH_KEY_PATH, AGENTS_RESOURCES_PATH + "/" + SSH_AGENT_NAME + "/" + SSH_KEY_PATH)
                    .withFileFromClasspath(
                            SSH_KEY_PUB_PATH, AGENTS_RESOURCES_PATH + "/" + SSH_AGENT_NAME + "/" + SSH_KEY_PUB_PATH)
                    .withFileFromClasspath(
                            SSH_SSHD_CONFIG, AGENTS_RESOURCES_PATH + "/" + SSH_AGENT_NAME + "/" + SSH_SSHD_CONFIG)
                    .withFileFromClasspath(DOCKERFILE, AGENTS_RESOURCES_PATH + "/" + SSH_AGENT_NAME + "/" + DOCKERFILE))
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
