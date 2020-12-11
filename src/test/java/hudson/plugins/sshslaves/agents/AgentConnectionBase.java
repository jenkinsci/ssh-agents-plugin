package hudson.plugins.sshslaves.agents;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.SystemUtils;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.plugins.sshslaves.verifiers.NonVerifyingKeyVerificationStrategy;
import hudson.slaves.DumbSlave;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;

/**
 * Base class to test connections to a remote SSH Agent
 *
 * @author Kuisathaverat
 */
public class AgentConnectionBase {
  public static final String USER = "jenkins";
  public static final String PASSWORD = "password";
  public static final String AGENT_WORK_DIR = "/home/jenkins";
  public static final int SSH_PORT = 22;
  public static final String SSH_SSHD_CONFIG = "ssh/sshd_config";
  public static final String DOCKERFILE = "Dockerfile";
  public static final String SSH_AUTHORIZED_KEYS = "ssh/authorized_keys";
  public static final String AGENTS_RESOURCES_PATH = "/hudson/plugins/sshslaves/agents/";

  @Rule
  public JenkinsRule j = new JenkinsRule();

  @Before
  public void isLinuxOrMac(){
    Assume.assumeTrue(SystemUtils.IS_OS_LINUX || SystemUtils.IS_OS_MAC);
  }

  protected boolean isSuccessfullyConnected(Node node) throws IOException, InterruptedException {
    boolean ret = false;
    int count = 0;
    while (count < 10) {
      Thread.sleep(1000);
      String log = node.toComputer().getLog();
      ret = log.contains("Agent successfully connected and online");
      count++;
    }
    return ret;
  }

  protected void waitForAgentConnected(Node node) throws InterruptedException {
    int count = 0;
    while (!node.toComputer().isOnline() && count < 120) {
      Thread.sleep(1000);
      count++;
    }
  }

  protected Node createPermanentAgent(String name, String host, int sshPort, String keyResourcePath, String passphrase)
    throws Descriptor.FormException, IOException {
    String credId = "sshCredentialsId";
    createSshCredentials(credId, keyResourcePath, passphrase);
    final SSHLauncher launcher = new SSHLauncher(host , sshPort, credId);
    launcher.setSshHostKeyVerificationStrategy(new NonVerifyingKeyVerificationStrategy());
    DumbSlave agent = new DumbSlave(name, AGENT_WORK_DIR, launcher);
    j.jenkins.addNode(agent);
    return j.jenkins.getNode(agent.getNodeName());
  }

  private void createSshCredentials(String id, String keyResourcePath, String passphrase) throws IOException {
    String privateKey = IOUtils.toString(getClass().getResourceAsStream(keyResourcePath), StandardCharsets.UTF_8);
    BasicSSHUserPrivateKey.DirectEntryPrivateKeySource privateKeySource = new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(
      privateKey);
    BasicSSHUserPrivateKey credentials = new BasicSSHUserPrivateKey(CredentialsScope.SYSTEM, id, USER, privateKeySource,
                                                                    passphrase, "Private Key ssh credentials");
    SystemCredentialsProvider.getInstance().getDomainCredentialsMap().put(Domain.global(),
                                                                          Collections.singletonList(credentials));
  }
}
