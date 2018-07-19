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

import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.Fingerprint;
import hudson.plugins.sshslaves.verifiers.KnownHostsFileKeyVerificationStrategy;
import hudson.slaves.NodeProperty;
import hudson.tools.JDKInstaller;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.Collections;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainSpecification;
import com.cloudbees.plugins.credentials.domains.HostnamePortSpecification;
import hudson.model.Computer;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Node.Mode;
import hudson.model.Slave;
import hudson.slaves.DumbSlave;
import hudson.slaves.RetentionStrategy;
import java.util.concurrent.ExecutionException;

import hudson.util.VersionNumber;
import org.jenkinsci.test.acceptance.docker.DockerRule;
import org.jenkinsci.test.acceptance.docker.fixtures.JavaContainer;
import org.junit.Assert;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.ClassRule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;

public class SSHLauncherTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public DockerRule<JavaContainer> javaContainer = new DockerRule<>(JavaContainer.class);

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
        final InputStream versionStream = SSHLauncherTest.class
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
    
    @Test
    public void configurationRoundtrip() throws Exception {
        SystemCredentialsProvider.getInstance().getDomainCredentialsMap().put(Domain.global(),
                Collections.<Credentials>singletonList(
                        new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM, "dummyCredentialId", null, "user", "pass")
                )
        );
        SSHLauncher launcher = new SSHLauncher("localhost", 123, "dummyCredentialId", null, "xyz", null, null, 1, 1, 1, new KnownHostsFileKeyVerificationStrategy());
        DumbSlave slave = new DumbSlave("slave", "dummy",
                j.createTmpDir().getPath(), "1", Mode.NORMAL, "",
                launcher, RetentionStrategy.NOOP, Collections.<NodeProperty<?>>emptyList());
        j.jenkins.addNode(slave);

        HtmlPage p = j.createWebClient().getPage(slave, "configure");
        j.submit(p.getFormByName("config"));
        Slave n = (Slave) j.jenkins.getNode("slave");

        assertNotSame(n,slave);
        assertNotSame(n.getLauncher(),launcher);
        j.assertEqualDataBoundBeans(n.getLauncher(),launcher);
    }


    @Test
    public void fillCredentials() {
        SystemCredentialsProvider.getInstance().getDomainCredentialsMap().put(
                new Domain("test", null, Collections.<DomainSpecification>singletonList(
                        new HostnamePortSpecification(null, null)
                )),
                Collections.<Credentials>singletonList(
                        new BasicSSHUserPrivateKey(CredentialsScope.SYSTEM, "dummyCredentialId", "john", null, null, null)
                )
        );

        SSHLauncher.DescriptorImpl desc = (SSHLauncher.DescriptorImpl) j.jenkins.getDescriptorOrDie(SSHLauncher.class);
        assertEquals(2, desc.doFillCredentialsIdItems(j.jenkins, "", "22", "does-not-exist").size());
        assertEquals(1, desc.doFillCredentialsIdItems(j.jenkins, "", "22", "dummyCredentialId").size());
        assertEquals(1, desc.doFillCredentialsIdItems(j.jenkins, "", "forty two", "does-not-exist").size());
        assertEquals(1, desc.doFillCredentialsIdItems(j.jenkins, "", "", "does-not-exist").size());
    }

    @Issue("JENKINS-38832")
    @Test
    public void trackCredentialsWithUsernameAndPassword() throws Exception {
        UsernamePasswordCredentialsImpl credentials = new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM, "dummyCredentialId", null, "user", "pass");
        SystemCredentialsProvider.getInstance().getDomainCredentialsMap().put(Domain.global(),
          Collections.<Credentials>singletonList(
            credentials
          )
        );
        SSHLauncher launcher = new SSHLauncher("localhost", 123, "dummyCredentialId", null, "xyz", null, null, 1, 1, 1);
        DumbSlave slave = new DumbSlave("slave", "dummy",
          j.createTmpDir().getPath(), "1", Mode.NORMAL, "",
          launcher, RetentionStrategy.NOOP, Collections.<NodeProperty<?>>emptyList());

        Fingerprint fingerprint = CredentialsProvider.getFingerprintOf(credentials);
        assertThat("No fingerprint created until use", fingerprint, nullValue());

        j.jenkins.addNode(slave);

        fingerprint = CredentialsProvider.getFingerprintOf(credentials);
        assertThat(fingerprint, notNullValue());
    }

    @Issue("JENKINS-38832")
    @Test
    public void trackCredentialsWithUsernameAndPrivateKey() throws Exception {
        BasicSSHUserPrivateKey credentials = new BasicSSHUserPrivateKey(CredentialsScope.SYSTEM, "dummyCredentialId", "user", null, "", "desc");
        SystemCredentialsProvider.getInstance().getDomainCredentialsMap().put(Domain.global(),
          Collections.<Credentials>singletonList(
            credentials
          )
        );
        SSHLauncher launcher = new SSHLauncher("localhost", 123, "dummyCredentialId", null, "xyz", null, null, 1, 1, 1);
        DumbSlave slave = new DumbSlave("slave", "dummy",
          j.createTmpDir().getPath(), "1", Mode.NORMAL, "",
          launcher, RetentionStrategy.NOOP, Collections.<NodeProperty<?>>emptyList());

        Fingerprint fingerprint = CredentialsProvider.getFingerprintOf(credentials);
        assertThat("No fingerprint created until use", fingerprint, nullValue());

        j.jenkins.addNode(slave);

        fingerprint = CredentialsProvider.getFingerprintOf(credentials);
        assertThat(fingerprint, notNullValue());
    }

    @Issue("JENKINS-44830")
    @Test
    public void upgrade() throws Exception {
        JavaContainer c = javaContainer.get();
        DumbSlave slave = new DumbSlave("slave" + j.jenkins.getNodes().size(),
                "dummy", "/home/test/slave", "1", Node.Mode.NORMAL, "remote",
                // Old constructor passes null sshHostKeyVerificationStrategy:
                new SSHLauncher(c.ipBound(22), c.port(22), "test", "test", "", ""),
                RetentionStrategy.INSTANCE, Collections.<NodeProperty<?>>emptyList());
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

}
