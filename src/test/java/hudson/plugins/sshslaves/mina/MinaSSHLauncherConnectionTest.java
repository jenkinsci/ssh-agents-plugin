package hudson.plugins.sshslaves.mina;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Computer;
import hudson.model.FreeStyleProject;
import hudson.model.Node.Mode;
import hudson.slaves.DumbSlave;
import hudson.slaves.OfflineCause;
import hudson.slaves.RetentionStrategy;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Tests basic SSH connection with password credentials and reconnection behavior.
 * Ported from CloudBees SSHLauncherTest.
 */
@Timeout(value = 10, unit = TimeUnit.MINUTES)
@WithJenkins
@Testcontainers(disabledWithoutDocker = true)
class MinaSSHLauncherConnectionTest {

    @Container
    static GenericContainer<?> sshContainer = MinaSSHContainerFactory.createContainer("base");

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule j) {
        this.j = j;
    }

    @Test
    void logSurvivesReconnections() throws Exception {
        String host = sshContainer.getHost();
        int port = sshContainer.getMappedPort(22);

        SystemCredentialsProvider.getInstance()
                .getDomainCredentialsMap()
                .put(
                        Domain.global(),
                        Collections.singletonList(new UsernamePasswordCredentialsImpl(
                                CredentialsScope.SYSTEM, "simpleCredentials", null, "foo", "beer")));

        MinaSSHLauncher launcher = new MinaSSHLauncher(host, port, "simpleCredentials");
        launcher.setJavaPath("/usr/java/latest/bin/java");
        launcher.setServerKeyVerificationStrategy(new BlindTrustVerificationStrategy());

        DumbSlave agent = new DumbSlave("agent" + j.jenkins.getNodes().size(), "/home/foo/agent", launcher);
        agent.setMode(Mode.NORMAL);
        agent.setRetentionStrategy(RetentionStrategy.INSTANCE);
        j.jenkins.addNode(agent);

        Computer computer = agent.toComputer();
        try {
            computer.connect(false).get();
        } catch (ExecutionException x) {
            throw new AssertionError("failed to connect: " + computer.getLog(), x);
        }

        assertThat(computer.getLog(), containsString("Agent successfully connected and online"));

        FreeStyleProject p = j.createFreeStyleProject();
        p.setAssignedNode(agent);

        try {
            computer.disconnect(OfflineCause.create(null)).get();
        } catch (ExecutionException x) {
            throw new AssertionError("failed to disconnect: " + computer.getLog(), x);
        }

        // Wait for the real disconnection
        Awaitility.await().atMost(Duration.ofSeconds(30)).until(() -> computer.getLog()
                .contains("Connection terminated"));

        try {
            computer.connect(true).get();
        } catch (ExecutionException x) {
            throw new AssertionError("failed to connect: " + computer.getLog(), x);
        }

        assertThat(computer.getLog(), containsString("Agent successfully connected and online"));
        j.buildAndAssertSuccess(p);
    }
}
