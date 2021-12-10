package io.jenkins.plugins.sshbuildagents.ssh.agents;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import io.jenkins.plugins.sshbuildagents.ssh.Connection;
import io.jenkins.plugins.sshbuildagents.ssh.FakeSSHKeyCredential;
import io.jenkins.plugins.sshbuildagents.ssh.ShellChannel;
import io.jenkins.plugins.sshbuildagents.ssh.mina.ConnectionImpl;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.session.SessionHeartbeatController;
import org.apache.sshd.core.CoreModuleProperties;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.plugins.sshslaves.categories.AgentSSHTest;
import hudson.plugins.sshslaves.categories.SSHKeyAuthenticationTest;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import static io.jenkins.plugins.sshbuildagents.ssh.agents.AgentConnectionBase.AGENTS_RESOURCES_PATH;
import static io.jenkins.plugins.sshbuildagents.ssh.agents.AgentConnectionBase.DOCKERFILE;
import static io.jenkins.plugins.sshbuildagents.ssh.agents.AgentConnectionBase.SSH_AUTHORIZED_KEYS;
import static io.jenkins.plugins.sshbuildagents.ssh.agents.AgentConnectionBase.SSH_PORT;
import static io.jenkins.plugins.sshbuildagents.ssh.agents.AgentConnectionBase.SSH_SSHD_CONFIG;
import static io.jenkins.plugins.sshbuildagents.ssh.mina.ConnectionImpl.HEARTBEAT_INTERVAL;
import static io.jenkins.plugins.sshbuildagents.ssh.mina.ConnectionImpl.WINDOW_SIZE;
import static org.junit.Assert.assertTrue;

/**
 * Connect to a remote SSH Server with a plain Apache Mina SSHD client.
 *
 * @author Kuisathaverat
 */
@Category({ AgentSSHTest.class, SSHKeyAuthenticationTest.class})
public class ClientRSA512ConnectionTest {
  public static final String SSH_AGENT_NAME = "ssh-agent-rsa512";
  public static final String SSH_KEY_PATH = "ssh/rsa-512-key";
  public static final String SSH_KEY_PUB_PATH = "ssh/rsa-512-key.pub";
  public static final String USER = "jenkins";
  public static final String PASSWORD = "password";
  public static final long timeout = 30000L;

  @Rule
  public GenericContainer agentContainer = new GenericContainer(
    new ImageFromDockerfile(SSH_AGENT_NAME, false)
      .withFileFromClasspath(SSH_AUTHORIZED_KEYS, AGENTS_RESOURCES_PATH + "/" + SSH_AGENT_NAME + "/" + SSH_AUTHORIZED_KEYS)
      .withFileFromClasspath(SSH_KEY_PATH, AGENTS_RESOURCES_PATH + "/" + SSH_AGENT_NAME + "/" + SSH_KEY_PATH)
      .withFileFromClasspath(SSH_KEY_PUB_PATH, AGENTS_RESOURCES_PATH + "/" + SSH_AGENT_NAME + "/" + SSH_KEY_PUB_PATH)
      .withFileFromClasspath(SSH_SSHD_CONFIG, AGENTS_RESOURCES_PATH + "/" + SSH_AGENT_NAME + "/" + SSH_SSHD_CONFIG)
      .withFileFromClasspath(DOCKERFILE, AGENTS_RESOURCES_PATH + "/" + SSH_AGENT_NAME + "/" + DOCKERFILE))
      .withExposedPorts(22);

  @Before
  public void setup() throws IOException {
    Logger.getLogger("org.apache.sshd").setLevel(Level.FINE);
    Logger.getLogger("io.jenkins.plugins.sshbuildagents").setLevel(Level.FINE);
    Logger.getLogger("org.apache.sshd.common.io.nio2").setLevel(Level.FINE);
  }

    @Test
  public void connectionExecCommandTests() throws IOException, InterruptedException {
    int port = agentContainer.getMappedPort(SSH_PORT);
    String host = agentContainer.getHost();

    SshClient client = getSshClient();
    ClientSession session = getClientSession(client, USER, host, port, timeout, PASSWORD);

    session.executeRemoteCommand("sleep 600s", System.out, System.err, StandardCharsets.UTF_8);

    for(int i=0;i<300;i++){
      Thread.sleep(1000);
      assertTrue(session.isOpen());
    }

    session.close();
    client.stop();
    assertTrue(true);
  }

  @Test
  public void connectionChannelTests() throws IOException, InterruptedException {
    int port = agentContainer.getMappedPort(SSH_PORT);
    String host = agentContainer.getHost();

    SshClient client = getSshClient();

    ClientSession session = getClientSession(client, USER, host, port, timeout, PASSWORD);

    ChannelExec channel = session.createExecChannel("sleep 600s\n");
    //session.setSessionHeartbeat(SessionHeartbeatController.HeartbeatType.IGNORE, Duration.ofMillis(timeout));
    channel.setOut(System.out);
    channel.setIn(System.in);
    channel.open().verify(timeout, TimeUnit.MILLISECONDS);
    //channel.addChannelListener(channelListener);
    channel.waitFor(Collections.singleton(ClientChannelEvent.CLOSED), timeout);

    for(int i=0;i<300;i++){
      Thread.sleep(1000);
      assertTrue(session.isOpen());
    }

    session.close();
    client.stop();
    assertTrue(true);
  }

  @Test
  public void testRunLongConnection() throws IOException, InterruptedException {
    int port = agentContainer.getMappedPort(SSH_PORT);
    String host = agentContainer.getHost();
    Connection connection = new ConnectionImpl(host, port);
    StandardUsernameCredentials credentials = new FakeSSHKeyCredential();
    connection.setCredentials(credentials);
    ShellChannel shellChannel = connection.shellChannel();
    shellChannel.execCommand("sleep 500s");
    for(int i=0;i<300;i++){
      Thread.sleep(1000);
      assertTrue(connection.isOpen());
    }
    connection.close();
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

  private SshClient getSshClient() {
    SshClient client = SshClient.setUpDefaultClient();
    client = SshClient.setUpDefaultClient();

    CoreModuleProperties.WINDOW_SIZE.set(client, WINDOW_SIZE);
    CoreModuleProperties.TCP_NODELAY.set(client, true);
    CoreModuleProperties.HEARTBEAT_REQUEST.set(client, "keepalive@jenkins.io");
    CoreModuleProperties.HEARTBEAT_INTERVAL.set(client, Duration.ofMillis(HEARTBEAT_INTERVAL));
    CoreModuleProperties.HEARTBEAT_REPLY_WAIT.set(client, Duration.ofMillis(HEARTBEAT_INTERVAL*2));

    //CoreModuleProperties.BUFFERED_IO_OUTPUT_MAX_PENDING_WRITE_WAIT.set(client, Duration.ofMillis(10));

    CoreModuleProperties.IDLE_TIMEOUT.getRequired(client);
    CoreModuleProperties.NIO2_READ_TIMEOUT.getRequired(client);
    CoreModuleProperties.HEARTBEAT_REPLY_WAIT.getRequired(client);
    client.start();
    return client;
  }

}
