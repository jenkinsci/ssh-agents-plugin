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
import java.security.PublicKey;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.apache.sshd.SshServer;
import org.apache.sshd.common.util.OsUtils;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.command.ScpCommandFactory;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.shell.ProcessShellFactory;
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

import hudson.plugins.sshslaves.SSHLauncher;
import hudson.slaves.DumbSlave;
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
                        new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM, "dummyCredentialId",
                                                            null, "user", "pass")
                )
        );

        int port = findPort();
        startSshServer(port);
        SSHLauncher launcher = new SSHLauncher("localhost", port, "dummyCredentialId", null,
                                               "xyz", null, null,
                                               30, 1, 1,
                                               new ManuallyTrustedKeyVerificationStrategy(true));
        DumbSlave slave = new DumbSlave("test-slave", temporaryFolder.newFolder().getAbsolutePath(), launcher);
        jenkins.getInstance().addNode(slave);
        SlaveComputer computer = (SlaveComputer) jenkins.getInstance().getComputer("test-slave");

        try {
            computer.connect(false).get();
        } catch (ExecutionException ex){
            if (!ex.getMessage().startsWith("java.io.IOException: Slave failed")
                && !ex.getMessage().startsWith("java.io.IOException: Agent failed")) {
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

    private void startSshServer(int port) throws IOException {
        SshServer serverSsh = SshServer.setUpDefaultServer();
        serverSsh.setPort(port);
        SimpleGeneratorHostKeyProvider keysProvider = new SimpleGeneratorHostKeyProvider();
        keysProvider.setAlgorithm("RSA");
        keysProvider.loadKey("RSA");
        serverSsh.setKeyPairProvider(keysProvider);
        serverSsh.setCommandFactory(new ScpCommandFactory(new CommandFactory() {
            public Command createCommand(String command) {
                EnumSet<ProcessShellFactory.TtyOptions> ttyOptions;
                if (OsUtils.isUNIX()) {
                    ttyOptions = EnumSet.of(ProcessShellFactory.TtyOptions.ONlCr);
                } else {
                    ttyOptions = EnumSet.of(ProcessShellFactory.TtyOptions.Echo, ProcessShellFactory.TtyOptions.ICrNl,
                                            ProcessShellFactory.TtyOptions.ONlCr);
                }
                return new ProcessShellFactory(command.split(" "), ttyOptions).create();
            }
        }));
        serverSsh.setPasswordAuthenticator(new PasswordAuthenticator() {
            public boolean authenticate(String username, String password, ServerSession session) {
                return true;
            }
        });
        serverSsh.setPublickeyAuthenticator(new PublickeyAuthenticator() {
            public boolean authenticate(String username, PublicKey key, ServerSession session) {
                return true;
            }
        });
        serverSsh.start();
    }
}
