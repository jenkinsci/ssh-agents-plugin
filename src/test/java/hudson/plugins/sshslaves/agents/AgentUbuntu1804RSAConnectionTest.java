package hudson.plugins.sshslaves.agents;

import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.plugins.sshslaves.categories.AgentSSHTest;
import hudson.plugins.sshslaves.categories.SSHKeyAuthenticationTest;
import static org.junit.Assert.assertTrue;
import org.junit.Ignore;

/**
 * Connect to a remote SSH Agent
 *
 * @author Kuisathaverat
 */
@Ignore("Only for manual test.")
@Category({ AgentSSHTest.class, SSHKeyAuthenticationTest.class})
public class AgentUbuntu1804RSAConnectionTest extends AgentConnectionBase {
  public static final String SSH_AGENT_NAME = "ssh-agent-ubuntu-18.04";
  public static final String SSH_KEY_PATH = "ssh/rsa-key";
  public static final String SSH_KEY_PUB_PATH = "ssh/rsa-key.pub";

  @Rule
  public GenericContainer agentContainer = new GenericContainer(
    new ImageFromDockerfile(SSH_AGENT_NAME, false)
      .withFileFromClasspath(SSH_AUTHORIZED_KEYS, AGENTS_RESOURCES_PATH + "/" + SSH_AGENT_NAME + "/" + SSH_AUTHORIZED_KEYS)
      .withFileFromClasspath(SSH_KEY_PATH, AGENTS_RESOURCES_PATH + "/" + SSH_AGENT_NAME + "/" + SSH_KEY_PATH)
      .withFileFromClasspath(SSH_KEY_PUB_PATH, AGENTS_RESOURCES_PATH + "/" + SSH_AGENT_NAME + "/" + SSH_KEY_PUB_PATH)
      .withFileFromClasspath(SSH_SSHD_CONFIG, AGENTS_RESOURCES_PATH + "/" + SSH_AGENT_NAME + "/" + SSH_SSHD_CONFIG)
      .withFileFromClasspath(DOCKERFILE, AGENTS_RESOURCES_PATH + "/" + SSH_AGENT_NAME + "/" + DOCKERFILE))
      .withExposedPorts(22);

  @Test
  public void connectionTests() throws IOException, InterruptedException, Descriptor.FormException {
    Node node = createPermanentAgent(SSH_AGENT_NAME, agentContainer.getHost(), agentContainer.getMappedPort(SSH_PORT),
    SSH_AGENT_NAME + "/" + SSH_KEY_PATH, "");
    waitForAgentConnected(node);
    assertTrue(isSuccessfullyConnected(node));
  }

}
