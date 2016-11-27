package hudson.plugins.sshslaves.verifiers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.apache.sshd.SshServer;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.UserAuth;
import org.apache.sshd.server.auth.UserAuthPassword;
import org.apache.sshd.server.command.UnknownCommand;
import org.apache.sshd.server.keyprovider.PEMGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import hudson.model.Node.Mode;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.slaves.DumbSlave;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SlaveComputer;

public class TrustHostKeyActionTest {
    
    
    
    
    
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();
    
    @Rule
    public final JenkinsRule jenkins = new JenkinsRule();

    private static int findPort() throws IOException {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket();
            socket.bind(null);
            return socket.getLocalPort();
        } finally {
            if (null != socket) {
                socket.close();
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testSubmitNotAuthorised() throws Exception {

        SystemCredentialsProvider.getInstance().getDomainCredentialsMap().put(Domain.global(),
                Collections.<Credentials>singletonList(
                        new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM, "dummyCredentialId", null, "user", "pass")
                )
        );
        
        final int port = findPort();

        SshServer server = SshServer.setUpDefaultServer();
        server.setPort(port);
        server.setKeyPairProvider(new PEMGeneratorHostKeyProvider());
        server.setUserAuthFactories(Arrays.asList((NamedFactory<UserAuth>)new UserAuthPassword.Factory()));
        server.setCommandFactory(new CommandFactory() {

            @Override
            public Command createCommand(final String command) {
                return new UnknownCommand(command);
            }
            
        });
        server.setPasswordAuthenticator(new PasswordAuthenticator() {
            
            @Override
            public boolean authenticate(String username, String password, ServerSession session) {
                return true;
            }
        });

        server.start();
        
        
        SSHLauncher launcher = new SSHLauncher("localhost", port, "dummyCredentialId", null, "xyz", null, null, 30, 1, 1, new ManualTrustingHostKeyVerifier());
        DumbSlave slave = new DumbSlave("test-slave", "SSH Test slave",
                temporaryFolder.newFolder().getAbsolutePath(), "1", Mode.NORMAL, "",
                launcher, RetentionStrategy.NOOP, Collections.<NodeProperty<?>>emptyList());
        
        jenkins.getInstance().addNode(slave);
        SlaveComputer computer = (SlaveComputer) jenkins.getInstance().getComputer("test-slave");

        //launcher.launch(computer, ((TaskListener) (new StreamTaskListener(System.out, Charset.defaultCharset()))));
        try {
            computer.connect(false).get();
        } catch (ExecutionException ex){
            if (!ex.getMessage().startsWith("java.io.IOException: Slave failed")) {
                throw ex;
            }
        }
        
        List<TrustHostKeyAction> actions = computer.getActions(TrustHostKeyAction.class);
        assertEquals(1, actions.size());
        assertNull(actions.get(0).getExistingHostKey());
        
        HtmlPage p = jenkins.createWebClient().getPage(slave, actions.get(0).getUrlName());
        p.getElementByName("Yes").click();
        
        assertTrue(actions.get(0).isComplete());
        assertEquals(actions.get(0).getExistingHostKey(), actions.get(0).getHostKey());
        
        
    }
    
}
