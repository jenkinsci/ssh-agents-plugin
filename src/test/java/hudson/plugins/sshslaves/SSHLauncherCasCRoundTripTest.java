package hudson.plugins.sshslaves;

import hudson.model.Node;
import hudson.slaves.SlaveComputer;
import io.jenkins.plugins.casc.misc.RoundTripAbstractTest;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SSHLauncherCasCRoundTripTest extends RoundTripAbstractTest {
  @Override
  protected void assertConfiguredAsExpected(RestartableJenkinsRule restartableJenkinsRule, String s) {
    final Node node = r.j.jenkins.getNode("this-ssh-agent");
    assertNotNull(node);

    SlaveComputer computer = (SlaveComputer) node.toComputer();
    assertNotNull(computer);

    SSHLauncher launcher = (SSHLauncher) computer.getLauncher();
    assertNotNull(launcher);

    assertEquals("ssh-host", launcher.getHost());
    assertEquals(2222, launcher.getPort());
    assertEquals("-DuberImportantParam=uberImportantValue", launcher.getJvmOptions());
  }

  @Override
  protected String stringInLogExpected() {
    return "Setting class hudson.plugins.sshslaves.SSHLauncher.host = ssh-host";
  }

  @Override
  protected String configResource() {
    return "SSHCasCConfig.yml";
  }
}
