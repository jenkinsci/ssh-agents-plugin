package hudson.plugins.sshslaves.agents;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.Timeout;
import org.jvnet.hudson.test.JenkinsRule;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.plugins.sshslaves.rules.CheckIsDockerAvailable;
import hudson.plugins.sshslaves.rules.CheckIsLinuxOrMac;
import hudson.plugins.sshslaves.verifiers.NonVerifyingKeyVerificationStrategy;
import hudson.slaves.DumbSlave;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

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

  @ClassRule
  public static CheckIsLinuxOrMac isLinuxOrMac = new CheckIsLinuxOrMac();

  @ClassRule
  public static CheckIsDockerAvailable isDockerAvailable = new CheckIsDockerAvailable();

  @Rule
  public JenkinsRule j = new JenkinsRule();

  @Rule
  public Timeout globalTimeout= new Timeout(4, TimeUnit.MINUTES);

  protected boolean isSuccessfullyConnected(Node node) throws IOException, InterruptedException {
    int count = 0;
    while (count < 30) {
      Thread.sleep(1000);
      String log = node.toComputer().getLog();
      if (log.contains("Agent successfully connected and online")) {
          return true;
      }
    }
    return false;
  }

  protected void waitForAgentConnected(Node node) throws InterruptedException {
    try {
      j.waitOnline((Slave) node);
    } catch (InterruptedException | RuntimeException x) {
      throw x;
    } catch (Exception x) {
      throw new RuntimeException(x);
    }
  }

  protected Node createPermanentAgent(String name, String host, int sshPort, String keyResourcePath, String passphrase)
    throws Descriptor.FormException, IOException {
    String credId = "sshCredentialsId";
    createSshKeyCredentials(credId, keyResourcePath, passphrase);
    final SSHLauncher launcher = new SSHLauncher(host , sshPort, credId);
    launcher.setSshHostKeyVerificationStrategy(new NonVerifyingKeyVerificationStrategy());
    DumbSlave agent = new DumbSlave(name, AGENT_WORK_DIR, launcher);
    j.jenkins.addNode(agent);
    return j.jenkins.getNode(agent.getNodeName());
  }

  protected Node createPermanentAgent(String name, String host, int sshPort)
        throws Descriptor.FormException, IOException {
      String credId = "sshCredentialsId";
      createSshCredentials(credId);
      final SSHLauncher launcher = new SSHLauncher(host , sshPort, credId);
      launcher.setSshHostKeyVerificationStrategy(new NonVerifyingKeyVerificationStrategy());
      DumbSlave agent = new DumbSlave(name, AGENT_WORK_DIR, launcher);
      j.jenkins.addNode(agent);
      return j.jenkins.getNode(agent.getNodeName());
  }

  private void createSshKeyCredentials(String id, String keyResourcePath, String passphrase) throws IOException {
    String privateKey = IOUtils.toString(getClass().getResourceAsStream(keyResourcePath), StandardCharsets.UTF_8);
    BasicSSHUserPrivateKey.DirectEntryPrivateKeySource privateKeySource = new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(
      privateKey);
    BasicSSHUserPrivateKey credentials = new BasicSSHUserPrivateKey(CredentialsScope.SYSTEM, id, USER, privateKeySource,
                                                                    passphrase, "Private Key ssh credentials");
    SystemCredentialsProvider.getInstance().getDomainCredentialsMap().put(Domain.global(),
                                                                          Collections.singletonList(credentials));
  }

  private void createSshCredentials(String id) throws IOException {
    StandardUsernameCredentials credentials =
      new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM, id, "", USER, PASSWORD);
    SystemCredentialsProvider.getInstance().getDomainCredentialsMap().put(Domain.global(),
                                                                          Collections.singletonList(credentials));
  }
}
