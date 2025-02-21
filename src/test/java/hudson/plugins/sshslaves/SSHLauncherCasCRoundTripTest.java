package hudson.plugins.sshslaves;

import hudson.model.Node;
import hudson.slaves.SlaveComputer;
import io.jenkins.plugins.casc.misc.junit.jupiter.AbstractRoundTripTest;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@WithJenkins
class SSHLauncherCasCRoundTripTest extends AbstractRoundTripTest {

    @Override
    protected void assertConfiguredAsExpected(JenkinsRule jenkins, String s) {
        final Node node = jenkins.jenkins.getNode("this-ssh-agent");
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
