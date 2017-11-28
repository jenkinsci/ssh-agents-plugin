/*
 * The MIT License
 *
 * Copyright (c) 2016, Michael Clarke
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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

import org.apache.sshd.server.SshServer;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.UserAuth;
import org.apache.sshd.server.auth.password.UserAuthPasswordFactory;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.scp.UnknownCommand;
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
        ServerSocket socket = new ServerSocket();;
        try {
            socket.bind(null);
            return socket.getLocalPort();
        } finally {
            socket.close();
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
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        server.setUserAuthFactories(Arrays.asList((NamedFactory<UserAuth>)new UserAuthPasswordFactory()));
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
        
        
        SSHLauncher launcher = new SSHLauncher("localhost", port, "dummyCredentialId", null, "xyz", null, null, 30, 1, 1, new ManuallyTrustedKeyVerificationStrategy(true));
        DumbSlave slave = new DumbSlave("test-slave", "SSH Test slave",
                temporaryFolder.newFolder().getAbsolutePath(), "1", Mode.NORMAL, "",
                launcher, RetentionStrategy.NOOP, Collections.<NodeProperty<?>>emptyList());
        
        jenkins.getInstance().addNode(slave);
        SlaveComputer computer = (SlaveComputer) jenkins.getInstance().getComputer("test-slave");

        try {
            computer.connect(false).get();
        } catch (ExecutionException ex){
            if (!ex.getMessage().startsWith("java.io.IOException: Slave failed") && !ex.getMessage().startsWith("java.io.IOException: Agent failed")) {
                throw ex;
            }
        }
        
        List<TrustHostKeyAction> actions = computer.getActions(TrustHostKeyAction.class);
        assertEquals(computer.getLog(), 1, actions.size());
        assertNull(actions.get(0).getExistingHostKey());
        
        HtmlPage p = jenkins.createWebClient().getPage(slave, actions.get(0).getUrlName());
        p.getElementByName("Yes").click();
        
        assertTrue(actions.get(0).isComplete());
        assertEquals(actions.get(0).getExistingHostKey(), actions.get(0).getHostKey());
        
        
    }
    
}
