package hudson.plugins.sshslaves;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Computer;
import hudson.plugins.sshslaves.verifiers.NonVerifyingKeyVerificationStrategy;
import hudson.slaves.DumbSlave;
import hudson.slaves.RetentionStrategy;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.sshd.client.future.DefaultOpenFuture;
import org.apache.sshd.client.future.OpenFuture;
import org.apache.sshd.common.channel.Channel;
import org.apache.sshd.common.channel.ChannelFactory;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.subsystem.SubsystemFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * The launch must fail, not hang, when the agent stops answering once the SSH connection is open.
 */
@WithJenkins
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class SSHLauncherAgentSetupTimeoutTest {

    private static final int SETUP_TIMEOUT_SECONDS = 5;
    private static final String CREDENTIALS_ID = "credentials";

    private JenkinsRule j;
    private SshServer sshd;
    private int originalSetupTimeout;

    @BeforeEach
    void beforeEach(JenkinsRule j) {
        this.j = j;
        originalSetupTimeout = SSHLauncher.AGENT_SETUP_TIMEOUT_SECONDS;
        SSHLauncher.AGENT_SETUP_TIMEOUT_SECONDS = SETUP_TIMEOUT_SECONDS;

        sshd = SshServer.setUpDefaultServer();
        sshd.setHost("localhost");
        sshd.setPort(0);
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        sshd.setPasswordAuthenticator((username, password, session) -> true);
        sshd.setCommandFactory((channel, command) -> new ExitOnEofCommand());
    }

    @AfterEach
    void afterEach() throws IOException {
        SSHLauncher.AGENT_SETUP_TIMEOUT_SECONDS = originalSetupTimeout;
        sshd.stop(true);
    }

    @Test
    void agentNeverConfirmsTheSessionChannel() throws Exception {
        sshd.setChannelFactories(List.of(new ChannelFactory() {
            @Override
            public String getName() {
                return "session";
            }

            @Override
            public Channel createChannel(Session session) {
                return new ChannelSession() {
                    @Override
                    public OpenFuture open(long recipient, long rwSize, long packetSize, Buffer buffer) {
                        return new DefaultOpenFuture(this, new Object());
                    }
                };
            }
        }));
        sshd.start();

        assertLaunchTimesOut();
    }

    @Test
    void agentNeverAnswersSftp() throws Exception {
        sshd.setSubsystemFactories(List.of(new SubsystemFactory() {
            @Override
            public String getName() {
                return "sftp";
            }

            @Override
            public Command createSubsystem(ChannelSession channel) {
                return new SilentCommand();
            }
        }));
        sshd.start();

        assertLaunchTimesOut();
    }

    private void assertLaunchTimesOut() throws Exception {
        SystemCredentialsProvider.getInstance()
                .getDomainCredentialsMap()
                .put(
                        Domain.global(),
                        Collections.singletonList(new UsernamePasswordCredentialsImpl(
                                CredentialsScope.SYSTEM, CREDENTIALS_ID, null, "user", "password")));
        SSHLauncher launcher = new SSHLauncher("localhost", sshd.getPort(), CREDENTIALS_ID);
        launcher.setSshHostKeyVerificationStrategy(new NonVerifyingKeyVerificationStrategy());
        DumbSlave agent = new DumbSlave("agent", "/home/jenkins/agent", launcher);
        agent.setRetentionStrategy(RetentionStrategy.NOOP);
        j.jenkins.addNode(agent);
        Computer computer = agent.toComputer();

        // twice, as a launch that timed out must not block the next one
        for (int i = 0; i < 2; i++) {
            // a launch that fails completes the future exceptionally, a launch that hangs times out
            assertThrows(ExecutionException.class, () -> computer.connect(false)
                    .get(SETUP_TIMEOUT_SECONDS * 6L, TimeUnit.SECONDS));
            String log = computer.getLog();
            assertThat(log, containsString("did not complete within " + SETUP_TIMEOUT_SECONDS + " seconds"));
            assertThat(log, not(containsString("Copying via SCP")));
        }
    }

    /**
     * Does nothing and exits once its input is closed, which the client does after the command is accepted.
     */
    private static class ExitOnEofCommand extends SilentCommand {
        @Override
        public void start(ChannelSession channel, Environment env) {
            Thread thread = new Thread(
                    () -> {
                        try {
                            while (in.read() != -1) {
                                // discard
                            }
                        } catch (IOException e) {
                            // the client went away
                        }
                        exitCallback.onExit(0);
                    },
                    "ExitOnEofCommand");
            thread.setDaemon(true);
            thread.start();
        }
    }

    /**
     * Never answers, like an agent that became unreachable.
     */
    private static class SilentCommand implements Command {
        protected InputStream in;
        protected ExitCallback exitCallback;

        @Override
        public void setInputStream(InputStream in) {
            this.in = in;
        }

        @Override
        public void setOutputStream(OutputStream out) {}

        @Override
        public void setErrorStream(OutputStream err) {}

        @Override
        public void setExitCallback(ExitCallback callback) {
            this.exitCallback = callback;
        }

        @Override
        public void start(ChannelSession channel, Environment env) {}

        @Override
        public void destroy(ChannelSession channel) {}
    }
}
