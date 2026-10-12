package hudson.plugins.sshslaves.mina;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.model.Node;
import hudson.slaves.SlaveComputer;
import io.jenkins.plugins.casc.misc.junit.jupiter.AbstractRoundTripTest;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class MinaSSHLauncherCasCRoundTripTest extends AbstractRoundTripTest {

    @Override
    protected void assertConfiguredAsExpected(JenkinsRule jenkins, String s) {
        final Node node = jenkins.jenkins.getNode("mina-ssh-agent");
        assertNotNull(node);

        SlaveComputer computer = (SlaveComputer) node.toComputer();
        assertNotNull(computer);

        MinaSSHLauncher launcher = assertInstanceOf(MinaSSHLauncher.class, computer.getLauncher());

        assertEquals("ssh-host", launcher.getHost());
        assertEquals(2222, launcher.getPort());
        assertEquals("-Xmx256m", launcher.getJvmOptions());
        assertEquals("creds", launcher.getCredentialsId());
        assertInstanceOf(BlindTrustVerificationStrategy.class, launcher.getServerKeyVerificationStrategy());
    }

    @Override
    protected String stringInLogExpected() {
        return "Setting class hudson.plugins.sshslaves.mina.MinaSSHLauncher.host = ssh-host";
    }

    @Override
    protected String configResource() {
        return "MinaSSHCasCConfig.yml";
    }
}
