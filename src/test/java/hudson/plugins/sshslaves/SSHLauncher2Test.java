/*
 * The MIT License
 *
 * Copyright (c) 2004-, all the contributors
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
package hudson.plugins.sshslaves;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.test.acceptance.docker.DockerRule;
import org.jenkinsci.test.acceptance.docker.fixtures.JavaContainer;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Fingerprint;
import hudson.model.FreeStyleProject;
import hudson.model.JDK;
import hudson.model.Node.Mode;
import hudson.model.Slave;
import hudson.plugins.sshslaves.verifiers.KnownHostsFileKeyVerificationStrategy;
import hudson.plugins.sshslaves.verifiers.NonVerifyingKeyVerificationStrategy;
import hudson.security.ACL;
import hudson.slaves.DumbSlave;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SlaveComputer;
import hudson.tools.ToolLocationNodeProperty;
import hudson.util.VersionNumber;
import jenkins.model.Jenkins;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.HostnamePortSpecification;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import static hudson.plugins.sshslaves.SSHModularLauncher.WORK_DIR_PARAM;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SSHLauncher2Test {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public DockerRule<JavaContainer> javaContainer = new DockerRule<>(JavaContainer.class);

    @Rule
    public TemporaryFolder temporalFolder = new TemporaryFolder();

    @Test
    public void checkJavaVersionOpenJDK7NetBSD() throws Exception {
        VersionNumber java7 = new VersionNumber("7");
        if(JavaProvider.getMinJavaLevel().equals(java7)) {
            assertTrue("OpenJDK7 on NetBSD should be supported", checkSupported("openjdk-7-netbsd.version"));
        } else {
            assertNotSupported("openjdk-7-netbsd.version");
        }
    }

    @Test
    public void checkJavaVersionOpenJDK6Linux() throws Exception {
        assertNotSupported("openjdk-6-linux.version");   
    }

    @Test
    public void checkJavaVersionSun6Linux() throws Exception {
        assertNotSupported("sun-java-1.6-linux.version");
    }

    @Test
    public void checkJavaVersionSun6Mac() throws Exception {
        assertNotSupported("sun-java-1.6-mac.version");
    }

    @Test
    public void testCheckJavaVersionOracle7Mac() throws Exception {
        VersionNumber java7 = new VersionNumber("7");
        if(JavaProvider.getMinJavaLevel().equals(java7)) {
            Assert.assertTrue("Oracle 7 on Mac should be supported", checkSupported("oracle-java-1.7-mac.version"));
        } else {
            assertNotSupported("oracle-java-1.7-mac.version");
        }
    }

    @Test
    public void testCheckJavaVersionOracle8Mac() throws Exception {
        Assert.assertTrue("Oracle 8 on Mac should be supported", checkSupported("oracle-java-1.8-mac.version"));
    }

    @Test
    public void checkJavaVersionSun4Linux() throws IOException {
        assertNotSupported("sun-java-1.4-linux.version");
    }
    
    /**
     * Returns true if the version is supported.
     *
     * @param testVersionOutput
     *            the resource to find relative to this class that contains the
     *            output of "java -version"
     */
    private static boolean checkSupported(final String testVersionOutput) throws IOException {
        final String javaCommand = "testing-java";
        final InputStream versionStream = SSHLauncher2Test.class
                .getResourceAsStream(testVersionOutput);
        final BufferedReader r = new BufferedReader(new InputStreamReader(
                versionStream));
        final StringWriter output = new StringWriter();
        final String result = new JavaVersionChecker(null,null,null,null)
                .checkJavaVersion(System.out,javaCommand, r, output);
        return null != result;
    }

    private static void assertNotSupported(final String testVersionOutput) throws AssertionError, IOException {
        try {
            checkSupported(testVersionOutput);
            fail("Expected version " + testVersionOutput + " to be not supported, but it is supported");
        } catch (IOException e) {
            // expected
        }
    }

    private void checkRoundTrip(String host) throws Exception {
        UsernamePasswordCredentialsImpl credentilas = new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM,
                                                                                "dummyCredentialId", null, "user",
                                                                                "pass");
        getDomainCredentialsMap().put(Domain.global(), Collections.singletonList(credentilas));
        SSHModularLauncher launcher = createSSHLauncher2(host);

        assertEquals(host.trim(), launcher.getHost());
        DumbSlave agent = createDumbSlave(launcher);
        j.jenkins.addNode(agent);

        HtmlPage p = j.createWebClient().getPage(agent, "configure");
        j.submit(p.getFormByName("config"));
        Slave n = (Slave) j.jenkins.getNode(agent.getNodeName());

        assertNotSame(n, agent);
        assertNotSame(n.getLauncher(), launcher);
        j.assertEqualDataBoundBeans(n.getLauncher(), launcher);
    }

    @Test
    public void configurationRoundTrip() throws Exception {
        checkRoundTrip("localhost");
    }


    @Test
    public void fillCredentials() {
        getDomainCredentialsMap().put(
                new Domain("test", null, Collections.singletonList(
                        new HostnamePortSpecification(null, null)
                )),
                Collections.singletonList(
                        new BasicSSHUserPrivateKey(CredentialsScope.SYSTEM, "dummyCredentialId", "john", null, null, null)
                )
        );

        SSHModularLauncher.DescriptorImpl desc = (SSHModularLauncher.DescriptorImpl) j.jenkins.getDescriptorOrDie(SSHModularLauncher.class);
        assertEquals(2, desc.doFillCredentialsIdItems(j.jenkins, "", "22", "does-not-exist").size());
        assertEquals(1, desc.doFillCredentialsIdItems(j.jenkins, "", "22", "dummyCredentialId").size());
        assertEquals(1, desc.doFillCredentialsIdItems(j.jenkins, "", "forty two", "does-not-exist").size());
        assertEquals(1, desc.doFillCredentialsIdItems(j.jenkins, "", "", "does-not-exist").size());
    }

    @Test
    public void trimWhiteSpace() throws Exception {
        checkRoundTrip("   localhost");
        checkRoundTrip("localhost    ");
        checkRoundTrip("   localhost    ");
    }

    @Issue("JENKINS-38832")
    @Test
    public void trackCredentialsWithUsernameAndPassword() throws Exception {
        UsernamePasswordCredentialsImpl credentials = new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM, "dummyCredentialId", null, "user", "pass");
        getDomainCredentialsMap().put(Domain.global(), Collections.singletonList(credentials));
        SSHModularLauncher launcher = createSSHLauncher2("localhost");

        DumbSlave slave = createDumbSlave(launcher);

        Fingerprint fingerprint = CredentialsProvider.getFingerprintOf(credentials);
        assertThat("No fingerprint created until use", fingerprint, nullValue());

        j.jenkins.addNode(slave);
        while (slave.toComputer().isConnecting()) {
            // Make sure verification takes place after launch is complete
            Thread.sleep(100);
        }

        fingerprint = CredentialsProvider.getFingerprintOf(credentials);
        assertThat(fingerprint, notNullValue());
    }

    @Issue("JENKINS-38832")
    @Test
    public void trackCredentialsWithUsernameAndPrivateKey() throws Exception {
        BasicSSHUserPrivateKey credentials = new BasicSSHUserPrivateKey(CredentialsScope.SYSTEM, "dummyCredentialId", "user", null, "", "desc");
        getDomainCredentialsMap().put(Domain.global(), Collections.singletonList(credentials));

        SSHModularLauncher launcher = createSSHLauncher2("localhost");
        launcher.setCredentialsId(credentials.getId());

        DumbSlave slave = createDumbSlave(launcher);

        Fingerprint fingerprint = CredentialsProvider.getFingerprintOf(credentials);
        assertThat("No fingerprint created until use", fingerprint, nullValue());

        j.jenkins.addNode(slave);
        while (slave.toComputer().isConnecting()) {
            // Make sure verification takes place after launch is complete
            Thread.sleep(100);
        }

        fingerprint = CredentialsProvider.getFingerprintOf(credentials);
        assertThat(fingerprint, notNullValue());
    }

    @Test
    public void connectionTest() throws Exception {
        JavaContainer c = javaContainer.get();
        StandardUsernameCredentials credential = createGobalCredential("test", "test");

        SSHModularLauncher launcher = new SSHModularLauncher(c.ipBound(22), credential.getId(), new NonVerifyingKeyVerificationStrategy());
        launcher.setPort(c.port(22));
        launcher.setMaxNumRetries(1);
        launcher.setLaunchTimeoutSeconds(30);

        DumbSlave slave = new DumbSlave("agent" + j.jenkins.getNodes().size(), "/home/test/slave", launcher);
        slave.setRetentionStrategy(RetentionStrategy.INSTANCE);
        slave.setMode(Mode.NORMAL);

        j.jenkins.addNode(slave);
        Computer computer = slave.toComputer();
        try {
            computer.connect(false).get();
        } catch (ExecutionException x) {
            throw new AssertionError("failed to connect: " + computer.getLog(), x);
        }
        FreeStyleProject p = j.createFreeStyleProject();
        p.setAssignedNode(slave);
        j.buildAndAssertSuccess(p);
    }

    @Issue("JENKINS-44111")
    @Test
    public void workDirTest() throws Exception {
        String rootFS = "/home/user";
        String anotherWorkDir = "/another/workdir";

        SSHModularLauncher launcher = new SSHModularLauncher("Hostname", "credentialID", new NonVerifyingKeyVerificationStrategy());
        //use rootFS
        Assert.assertEquals(launcher.getWorkDirParam(rootFS), WORK_DIR_PARAM + rootFS);

        launcher = new SSHModularLauncher("Hostname", "credentialID", new NonVerifyingKeyVerificationStrategy());
        launcher.setSuffixStartSlaveCmd("suffix" + WORK_DIR_PARAM + anotherWorkDir);

        //if worDir is in suffix return ""
        Assert.assertEquals(launcher.getWorkDirParam(rootFS), "");
        //if worDir is in suffix return "", even do you set workDir in configuration
        launcher.setWorkDir(anotherWorkDir);
        Assert.assertEquals(launcher.getWorkDirParam(rootFS), "");

        launcher = new SSHModularLauncher("Hostname", "credentialID", new NonVerifyingKeyVerificationStrategy());
        //user the workDir set in configuration
        launcher.setWorkDir(anotherWorkDir);
        Assert.assertEquals(launcher.getWorkDirParam(rootFS), WORK_DIR_PARAM + anotherWorkDir);
    }

    @Issue("JENKINS-53245")
    @Test
    public void setJavaHome() throws Exception {
        String javaHome = "/java_home";
        String javaHomeTool = "/java_home_tool";

        DumbSlave slave = createDumbSlave(createSSHLauncher2("hostname"));
        j.jenkins.addNode(slave);
        SlaveComputer computer = slave.getComputer();

        List<EnvironmentVariablesNodeProperty.Entry> env = new ArrayList<>();
        EnvironmentVariablesNodeProperty.Entry entry = new EnvironmentVariablesNodeProperty.Entry(DefaultJavaProvider.JAVA_HOME, javaHome);
        env.add(entry);
        NodeProperty<?> javaHomeProperty = new EnvironmentVariablesNodeProperty(env);

        List<ToolLocationNodeProperty.ToolLocation> locations = new ArrayList<>();
        JDK.DescriptorImpl jdkType = Jenkins.getInstance().getDescriptorByType(JDK.DescriptorImpl.class);
        ToolLocationNodeProperty tool = new ToolLocationNodeProperty(new ToolLocationNodeProperty.ToolLocation(
                jdkType, "toolJdk", javaHomeTool));

        List<NodeProperty<?>> properties = new ArrayList<>();
        properties.add(javaHomeProperty);
        properties.add(tool);
        computer.getNode().setNodeProperties(properties);

        JavaProvider provider = new DefaultJavaProvider();
        List<String> javas = provider.getJavas(computer, null, null);
        assertTrue(javas.contains(javaHome + DefaultJavaProvider.BIN_JAVA));
        assertTrue(javas.contains(javaHomeTool + DefaultJavaProvider.BIN_JAVA));
        assertTrue(javas.contains(SSHModularLauncher.getWorkingDirectory(computer) + DefaultJavaProvider.JDK_BIN_JAVA));
    }

    /**
     *
     * @param host hostname to set.
     * @return a basic launcher for the hostname, it is not valid for real connections.
     */
    private SSHModularLauncher createSSHLauncher2(String host) {
        SSHModularLauncher launcher = new SSHModularLauncher(host, "dummyCredentialId", new KnownHostsFileKeyVerificationStrategy());
        launcher.setPort(123);
        launcher.setJavaPath("xyz");
        launcher.setMaxNumRetries(1);
        launcher.setLaunchTimeoutSeconds(10);
        return launcher;
    }

    /**
     * @param launcher launcher to use.
     * @return a basic DumbSlave.
     * @throws Descriptor.FormException on error.
     * @throws IOException on error.
     */
    private DumbSlave createDumbSlave(SSHModularLauncher launcher) throws Descriptor.FormException, IOException {
        DumbSlave slave = new DumbSlave("agent" + j.jenkins.getNodes().size(), temporalFolder.newFolder().getPath(),
                                        launcher);
        slave.setRetentionStrategy(RetentionStrategy.NOOP);
        slave.setMode(Mode.NORMAL);
        return slave;
    }

    private Map<Domain, List<Credentials>> getDomainCredentialsMap() {
        return SystemCredentialsProvider.getInstance().getDomainCredentialsMap();
    }

    static StandardUsernameCredentials createGobalCredential(String username, String password) {
        username = StringUtils.isEmpty(username) ? System.getProperty("user.name") : username;

        StandardUsernameCredentials u = new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM, null, "", username, password);

        final SecurityContext securityContext = ACL.impersonate(ACL.SYSTEM);
        try {
            CredentialsStore s = CredentialsProvider.lookupStores(Jenkins.getInstance()).iterator().next();
            try {
                s.addCredentials(Domain.global(), u);
                return u;
            } catch (IOException e) {
                // ignore
            }
        } finally {
            SecurityContextHolder.setContext(securityContext);
        }
        return u;
    }
}
