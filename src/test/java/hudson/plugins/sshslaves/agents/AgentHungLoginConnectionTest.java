package hudson.plugins.sshslaves.agents;

import static hudson.plugins.sshslaves.tags.TestTags.AGENT_SSH_TEST;
import static hudson.plugins.sshslaves.tags.TestTags.SSH_KEY_AUTHENTICATION_TEST;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.plugins.sshslaves.Messages;
import hudson.plugins.sshslaves.SSHLauncher;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;

/**
 * The remote authenticates and then never finishes the command the launcher runs to check the login for junk, so the
 * launch must give up on its own, release the agent and leave no stale connection for the next attempt.
 */
@Tag(AGENT_SSH_TEST)
@Tag(SSH_KEY_AUTHENTICATION_TEST)
class AgentHungLoginConnectionTest extends AgentConnectionBaseTest {
    private static final String SSH_AGENT_NAME = "ssh-agent-rsa";
    private static final String SSH_KEY_PATH = "ssh/rsa-key";
    private static final String SSH_KEY_PUB_PATH = "ssh/rsa-key.pub";
    private static final Duration LIMIT = Duration.ofSeconds(90);

    @Container
    private static final GenericContainer<?> agentContainer = new GenericContainer<>(new ImageFromDockerfile(
                            SSH_AGENT_NAME + "-hung-login", false)
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

    /** ForceCommand replaces "exit 0" with a command that never closes stdout, as a stalled login does. */
    @BeforeAll
    static void hangLogin() throws IOException, InterruptedException {
        agentContainer.execInContainer(
                "sh", "-c", "echo 'ForceCommand sleep 3600' >> /etc/ssh/sshd_config && pkill -HUP -o sshd");
    }

    @Override
    protected void customizeLauncher(SSHLauncher launcher) {
        launcher.setLaunchTimeoutSeconds(1);
        launcher.setMaxNumRetries(0);
        launcher.setRetryWaitTime(0);
    }

    /** Replaces the inherited connection test: against a hung login it can only succeed by timing out. */
    @Test
    @Override
    void connectionTests() throws IOException, InterruptedException, Descriptor.FormException {
        Computer computer = createAgent();

        waitUntilLaunchEnded(computer);
        assertTrue(computer.isOffline());
        String log = computer.getLog();
        assertTrue(log.contains("did not complete within"), log);
        assertTrue(log.contains(Messages.SSHLauncher_launchCanceled()), log);

        assertNextLaunchRunsAgain(computer);
    }

    @Test
    void interruptedLaunchLeavesNoStaleConnection() throws IOException, InterruptedException, Descriptor.FormException {
        Computer computer = createAgent();

        assertTimeoutPreemptively(LIMIT, () -> {
            while (!computer.getLog().contains("Authentication successful")) {
                Thread.sleep(200);
            }
        });
        Thread launch = findLaunchThread();
        assertNotNull(launch, "no thread is waiting for the launch");
        launch.interrupt();

        waitUntilLaunchEnded(computer);
        assertNextLaunchRunsAgain(computer);
    }

    private Computer createAgent() throws IOException, Descriptor.FormException {
        Node node = createPermanentAgent(
                getAgentName(),
                agentContainer.getHost(),
                agentContainer.getMappedPort(SSH_PORT),
                getAgentSshKeyPath(),
                getAgentSshKeyPassphrase());
        return node.toComputer();
    }

    private static void waitUntilLaunchEnded(Computer computer) {
        assertTimeoutPreemptively(LIMIT, () -> {
            while (computer.isConnecting() || computer.getLog().isEmpty()) {
                Thread.sleep(200);
            }
        });
    }

    /** A second attempt must run a real launch instead of stopping at "already connected". */
    private static void assertNextLaunchRunsAgain(Computer computer) throws IOException {
        assertTimeoutPreemptively(LIMIT, () -> {
            assertThrows(ExecutionException.class, () -> {
                computer.connect(true).get();
            });
        });
        String log = computer.getLog();
        assertFalse(log.contains(Messages.SSHLauncher_alreadyConnected()), log);
        assertTrue(log.contains("Authentication successful"), log);
    }

    /** The thread that waits in {@code invokeAll} for the launch, not the launch worker itself. */
    private static Thread findLaunchThread() {
        return Thread.getAllStackTraces().entrySet().stream()
                .filter(e -> e.getKey().getName().startsWith("Computer.threadPoolForRemoting"))
                .filter(e -> Arrays.stream(e.getValue())
                        .anyMatch(f -> f.getClassName().equals(SSHLauncher.class.getName())
                                && f.getMethodName().equals("launch")))
                .map(java.util.Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

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
