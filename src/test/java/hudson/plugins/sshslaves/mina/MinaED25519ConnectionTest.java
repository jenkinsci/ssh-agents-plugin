package hudson.plugins.sshslaves.mina;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.model.Computer;
import hudson.model.FreeStyleProject;
import hudson.model.Node.Mode;
import hudson.slaves.DumbSlave;
import hudson.slaves.OfflineCause;
import hudson.slaves.RetentionStrategy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.IOUtils;
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
 * Tests SSH connection with ED25519 host key.
 * Ported from CloudBees ED25519SSHLauncherTest.
 */
@Timeout(value = 10, unit = TimeUnit.MINUTES)
@WithJenkins
@Testcontainers(disabledWithoutDocker = true)
class MinaED25519ConnectionTest {

    @Container
    static GenericContainer<?> sshContainer = MinaSSHContainerFactory.createContainer("ed25519");

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule j) {
        this.j = j;
    }

    @Test
    void sshConnectWithKeyTrustHost() throws Exception {
        String host = sshContainer.getHost();
        int port = sshContainer.getMappedPort(22);
        String privateKey = IOUtils.toString(
                Objects.requireNonNull(
                        Thread.currentThread().getContextClassLoader().getResourceAsStream("rsa2048")),
                StandardCharsets.UTF_8);

        Iterator<CredentialsStore> stores =
                CredentialsProvider.lookupStores(j.jenkins).iterator();
        assertTrue(stores.hasNext());
        CredentialsStore store = stores.next();

        store.addCredentials(
                Domain.global(),
                new BasicSSHUserPrivateKey(
                        CredentialsScope.SYSTEM,
                        "simpleCredentials",
                        "foo",
                        new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(privateKey),
                        "theaustraliancricketteamisthebest",
                        null));

        MinaSSHLauncher launcher = new MinaSSHLauncher(host, port, "simpleCredentials");
        launcher.setJavaPath("/usr/java/latest/bin/java");
        launcher.setServerKeyVerificationStrategy(new TrustOnFirstUseVerificationStrategy(false));

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
