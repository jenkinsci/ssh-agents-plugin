package hudson.plugins.sshslaves;

import hudson.model.Node;
import hudson.slaves.SlaveComputer;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SSHLauncherCasCSupportTest {
  @Rule
  public JenkinsConfiguredWithCodeRule j = new JenkinsConfiguredWithCodeRule();

  @Test
  @ConfiguredWithCode("SSHCasCConfig.yml")
  public void shouldBeAbleToConfigureSSHSlaves() {
    validateConfiguration();
  }

  @Test
  @ConfiguredWithCode("SSHCasCConfigLegacy.yml")
  public void shouldBeAbleToConfigureLegacySSHSlaves() {
    validateConfiguration();
  }

  private void validateConfiguration() {
    final Node node = j.jenkins.getNode("this-ssh-agent");
    assertNotNull(node);

    SlaveComputer computer = (SlaveComputer) node.toComputer();
    assertNotNull(computer);

    SSHLauncher launcher = (SSHLauncher) computer.getLauncher();
    assertNotNull(launcher);

    assertEquals("ssh-host", launcher.getHost());
    assertEquals(2222, launcher.getPort());
    assertEquals("-DuberImportantParam=uberImportantValue", launcher.getJvmOptions());
  }
}
