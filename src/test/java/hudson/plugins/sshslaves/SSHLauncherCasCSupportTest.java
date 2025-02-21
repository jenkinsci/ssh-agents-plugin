package hudson.plugins.sshslaves;

import hudson.model.Node;
import hudson.slaves.SlaveComputer;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@WithJenkinsConfiguredWithCode
class SSHLauncherCasCSupportTest {

    @Test
    @ConfiguredWithCode("SSHCasCConfig.yml")
    void shouldBeAbleToConfigureSSHSlaves(JenkinsConfiguredWithCodeRule j) {
        validateConfiguration(j);
    }

    @Test
    @ConfiguredWithCode("SSHCasCConfigLegacy.yml")
    void shouldBeAbleToConfigureLegacySSHSlaves(JenkinsConfiguredWithCodeRule j) {
        validateConfiguration(j);
    }

    private static void validateConfiguration(JenkinsConfiguredWithCodeRule j) {
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
