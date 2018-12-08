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
package hudson.plugins.sshuserslaves.verifiers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import hudson.model.Node.Mode;
import hudson.plugins.sshuserslaves.SSHLauncher;
import hudson.slaves.DumbSlave;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SlaveComputer;

public class TrustHostKeyActionTest {
    
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();
    
    @Rule
    public final JenkinsRule jenkins = new JenkinsRule();

    private static int findPort() throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(null);
            return socket.getLocalPort();
        }
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testSubmitNotAuthorised() throws Exception {

        SystemCredentialsProvider.getInstance().getDomainCredentialsMap().put(Domain.global(),
                Collections.singletonList(
                        new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM, "dummyCredentialId", null, "user", "pass")
                )
        );
        
        final int port = findPort();

        try {
            Object server = newSshServer();
            assertNotNull(server);
            Class keyPairProviderClass = newKeyPairProviderClass();
            Object provider = newProvider();
            assertNotNull(provider);
            Object factory = newFactory();
            assertNotNull(factory);
            Class commandFactoryClass = newCommandFactoryClass();
            Object commandFactory = newCommandFactory(commandFactoryClass);
            assertNotNull(commandFactory);
            Class commandAuthenticatorClass = newCommandAuthenticatorClass();
            Object authenticator = newAuthenticator(commandAuthenticatorClass);
            assertNotNull(authenticator);

            invoke(server, "setPort", new Class[] {Integer.TYPE}, new Object[] {port});
            invoke(server, "setKeyPairProvider", new Class[] {keyPairProviderClass}, new Object[] {provider});
            invoke(server, "setUserAuthFactories", new Class[] {List.class}, new Object[] {Collections.singletonList(factory)});
            invoke(server, "setCommandFactory", new Class[] {commandFactoryClass}, new Object[] {commandFactory});
            invoke(server, "setPasswordAuthenticator", new Class[] {commandAuthenticatorClass}, new Object[] {authenticator});

            invoke(server, "start", null, null);
        } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | IllegalAccessException | InstantiationException | IllegalArgumentException e) {
            throw new AssertionError("Check sshd-core version", e);
        }

        SSHLauncher launcher = new SSHLauncher("localhost", port, "dummyCredentialId", null, "xyz", null, null, 30, 1, 1, new ManuallyTrustedKeyVerificationStrategy(true));
        DumbSlave slave = new DumbSlave("test-slave", "SSH Test slave",
                temporaryFolder.newFolder().getAbsolutePath(), "1", Mode.NORMAL, "",
                launcher, RetentionStrategy.NOOP, Collections.emptyList());
        
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

    private Object newSshServer() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class serverClass;
        try {
            serverClass = Class.forName("org.apache.sshd.SshServer");
        } catch (ClassNotFoundException e) {
            serverClass = Class.forName("org.apache.sshd.server.SshServer");
        }

        return serverClass.getDeclaredMethod("setUpDefaultServer", null).invoke(null);
    }

    private Class newKeyPairProviderClass() throws ClassNotFoundException {
        Class keyPairProviderClass;
        try {
            keyPairProviderClass = Class.forName("org.apache.sshd.common.KeyPairProvider");
        } catch (ClassNotFoundException e) {
            keyPairProviderClass = Class.forName("org.apache.sshd.common.keyprovider.KeyPairProvider");
        }

        return keyPairProviderClass;
    }

    private Object newProvider() throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Class providerClass;
        try {
            providerClass = Class.forName("org.apache.sshd.server.keyprovider.PEMGeneratorHostKeyProvider");
        } catch (ClassNotFoundException e) {
            providerClass = Class.forName("org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider");
        }

        return providerClass.getConstructor().newInstance();
    }

    private Object newFactory() throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Class factoryClass;
        try {
            factoryClass = Class.forName("org.apache.sshd.server.auth.UserAuthPassword$Factory");
        } catch (ClassNotFoundException e) {
            factoryClass = Class.forName("org.apache.sshd.server.auth.password.UserAuthPasswordFactory");
        }

        return factoryClass.getConstructor().newInstance();
    }

    private Class newCommandFactoryClass() throws ClassNotFoundException {
        return Class.forName("org.apache.sshd.server.CommandFactory");
    }

    private Object newCommandFactory(Class commandFactoryClass) throws ClassNotFoundException, IllegalArgumentException {
        return java.lang.reflect.Proxy.newProxyInstance(
                commandFactoryClass.getClassLoader(),
                new java.lang.Class[]{commandFactoryClass},
                new java.lang.reflect.InvocationHandler() {

                    @Override
                    public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws java.lang.Throwable {

                        if (method.getName().equals("createCommand")) {
                            Class commandClass;
                            try {
                                commandClass = Class.forName("org.apache.sshd.server.command.UnknownCommand");
                            } catch (ClassNotFoundException e) {
                                commandClass = Class.forName("org.apache.sshd.server.scp.UnknownCommand");
                            }

                            return commandClass.getConstructor(String.class).newInstance(args[0]);
                        }

                        return null;
                    }
                });
    }

    private Class newCommandAuthenticatorClass() throws ClassNotFoundException {
        Class passwordAuthenticatorClass;
        try {
            passwordAuthenticatorClass = Class.forName("org.apache.sshd.server.PasswordAuthenticator");
        } catch(ClassNotFoundException e) {
            passwordAuthenticatorClass = Class.forName("org.apache.sshd.server.auth.password.PasswordAuthenticator");
        }

        return passwordAuthenticatorClass;
    }

    private Object newAuthenticator(Class passwordAuthenticatorClass) throws ClassNotFoundException, IllegalArgumentException {
        return java.lang.reflect.Proxy.newProxyInstance(
                passwordAuthenticatorClass.getClassLoader(),
                new java.lang.Class[]{passwordAuthenticatorClass},
                new java.lang.reflect.InvocationHandler() {

                    @Override
                    public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws java.lang.Throwable {

                        if (method.getName().equals("authenticate")) {
                            return Boolean.TRUE;
                        }

                        return null;
                    }
                });
    }

    private Object invoke(Object target, String methodName, Class[] parameterTypes, Object[] args) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return target.getClass().getMethod(methodName, parameterTypes).invoke(target, args);
    }
}
