package io.jenkins.plugins.sshbuildagents.ssh.agents;

import static hudson.plugins.sshslaves.tags.TestTags.AGENT_SSH_TEST;
import static hudson.plugins.sshslaves.tags.TestTags.SSH_KEX_TEST;
import static io.jenkins.plugins.sshbuildagents.ssh.agents.AgentConnectionBaseTest.AGENTS_RESOURCES_PATH;
import static io.jenkins.plugins.sshbuildagents.ssh.agents.AgentConnectionBaseTest.DOCKERFILE;
import static io.jenkins.plugins.sshbuildagents.ssh.agents.AgentConnectionBaseTest.SSH_AUTHORIZED_KEYS;
import static io.jenkins.plugins.sshbuildagents.ssh.agents.AgentConnectionBaseTest.SSH_PORT;
import static io.jenkins.plugins.sshbuildagents.ssh.agents.AgentConnectionBaseTest.SSH_SSHD_CONFIG;
import static io.jenkins.plugins.sshbuildagents.ssh.mina.ConnectionImpl.HEARTBEAT_INTERVAL;
import static io.jenkins.plugins.sshbuildagents.ssh.mina.ConnectionImpl.HEARTBEAT_MAX_RETRY;
import static io.jenkins.plugins.sshbuildagents.ssh.mina.ConnectionImpl.IDLE_SESSION_TIMEOUT;
import static io.jenkins.plugins.sshbuildagents.ssh.mina.ConnectionImpl.WINDOW_SIZE;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import io.jenkins.plugins.sshbuildagents.ssh.Connection;
import io.jenkins.plugins.sshbuildagents.ssh.FakeSSHKeyCredential;
import io.jenkins.plugins.sshbuildagents.ssh.ShellChannel;
import io.jenkins.plugins.sshbuildagents.ssh.mina.ConnectionImpl;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang.SystemUtils;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.util.io.input.NullInputStream;
import org.apache.sshd.core.CoreModuleProperties;
import org.junit.Before;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Connect to a remote SSH Server with a plain Apache Mina SSHD client.
 *
 * @author Kuisathaverat
 */
@Tag(AGENT_SSH_TEST)
@Tag(SSH_KEX_TEST)
@Testcontainers(disabledWithoutDocker = true)
public class ClientRSA512ConnectionTest {
    public static final String SSH_AGENT_NAME = "ssh-agent-rsa512";
    public static final String SSH_KEY_PATH = "ssh/rsa-512-key";
    public static final String SSH_KEY_PUB_PATH = "ssh/rsa-512-key.pub";
    public static final String USER = "jenkins";
    public static final String PASSWORD = "password";
    public static final long timeout = 30000L;

    @BeforeAll
    static void beforeAll() {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        assumeTrue(SystemUtils.IS_OS_MAC || SystemUtils.IS_OS_LINUX);
        assumeTrue(DockerClientFactory.instance().isDockerAvailable());
    }

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

    @Before
    public void setup() throws IOException {
        Logger.getLogger("org.apache.sshd").setLevel(Level.FINE);
        Logger.getLogger("io.jenkins.plugins.sshbuildagents").setLevel(Level.FINE);
        Logger.getLogger("org.apache.sshd.common.io.nio2").setLevel(Level.FINE);
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    public void connectionExecCommandTests() throws IOException, InterruptedException {
        Logger logger = Logger.getLogger("io.jenkins.plugins.sshbuildagents.ssh.agents");
        agentContainer.start();
        assertTrue(agentContainer.isRunning());
        int port = agentContainer.getMappedPort(SSH_PORT);
        String host = agentContainer.getHost();
        try (SshClient client = getSshClient();
                ByteArrayOutputStream baOut = new ByteArrayOutputStream()) {
            client.start();
            try (ClientSession session = getClientSession(client, USER, host, port, timeout, PASSWORD)) {
                session.executeRemoteCommand("sleep 300s", baOut, baOut, StandardCharsets.UTF_8);
                for (int i = 0; i < 300; i++) {
                    Thread.sleep(1000);
                    logger.info(baOut.toString());
                    assertTrue(session.isOpen());
                }
            }
        }
        assertTrue(true);
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    public void connectionChannelTests() throws IOException, InterruptedException {
        Logger logger = Logger.getLogger("io.jenkins.plugins.sshbuildagents.ssh.agents");
        agentContainer.start();
        assertTrue(agentContainer.isRunning());
        int port = agentContainer.getMappedPort(SSH_PORT);
        String host = agentContainer.getHost();

        try (SshClient client = getSshClient()) {
            client.start();
            try (ClientSession session = getClientSession(client, USER, host, port, timeout, PASSWORD)) {
                try (ChannelExec channel = session.createExecChannel("sleep 300s\n");
                        ByteArrayOutputStream baOut = new ByteArrayOutputStream();
                        NullInputStream nullIn = new NullInputStream()) {
                    channel.setOut(baOut);
                    channel.setIn(nullIn);
                    channel.open().verify(timeout, TimeUnit.MILLISECONDS);
                    channel.waitFor(Collections.singleton(ClientChannelEvent.CLOSED), timeout);
                    for (int i = 0; i < 300; i++) {
                        Thread.sleep(1000);
                        logger.info(baOut.toString());
                        assertTrue(session.isOpen());
                    }
                }
            }
        }
        assertTrue(true);
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    public void testRunLongConnection() throws IOException, InterruptedException {
        agentContainer.start();
        assertTrue(agentContainer.isRunning());
        int port = agentContainer.getMappedPort(SSH_PORT);
        String host = agentContainer.getHost();
        try (Connection connection = new ConnectionImpl(host, port)) {
            StandardUsernameCredentials credentials = new FakeSSHKeyCredential();
            connection.setCredentials(credentials);
            try (ShellChannel shellChannel = connection.shellChannel()) {
                shellChannel.execCommand("sleep 300s");
                for (int i = 0; i < 300; i++) {
                    Thread.sleep(1000);
                    assertTrue(connection.isOpen());
                }
            }
        }
        assertTrue(true);
    }

    private ClientSession getClientSession(
            SshClient client, String user, String host, int port, long timeout, String password) throws IOException {
        ConnectFuture connectionFuture = client.connect(user, host, port);
        connectionFuture.verify(timeout);
        ClientSession session = connectionFuture.getSession();
        session.addPasswordIdentity(password);
        AuthFuture auth = session.auth();
        auth.verify(timeout);
        return session;
    }

    // https://github.com/apache/mina-sshd/issues/460
    private SshClient getSshClient() {
        SshClient client = SshClient.setUpDefaultClient();
        client = SshClient.setUpDefaultClient();

        CoreModuleProperties.WINDOW_SIZE.set(client, WINDOW_SIZE);
        CoreModuleProperties.TCP_NODELAY.set(client, true);
        CoreModuleProperties.HEARTBEAT_REQUEST.set(client, "keepalive@jenkins.io");
        CoreModuleProperties.HEARTBEAT_INTERVAL.set(client, Duration.ofSeconds(HEARTBEAT_INTERVAL));
        CoreModuleProperties.HEARTBEAT_NO_REPLY_MAX.set(client, HEARTBEAT_MAX_RETRY);
        CoreModuleProperties.IDLE_TIMEOUT.set(client, Duration.ofMinutes(IDLE_SESSION_TIMEOUT));
        return client;
    }
}
