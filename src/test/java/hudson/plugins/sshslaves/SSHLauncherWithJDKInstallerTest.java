/*
 * The MIT License
 *
 * Copyright (c) 2017 CloudBees, Inc.
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

import hudson.model.Computer;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.slaves.DumbSlave;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import hudson.tools.JDKInstaller;
import hudson.util.FormValidation;
import org.jenkinsci.test.acceptance.docker.DockerRule;
import org.jenkinsci.test.acceptance.docker.fixtures.SshdContainer;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;
import java.util.concurrent.ExecutionException;

/**
 * Tests {@link SSHLauncher} with real {@link JDKInstaller}.
 * @author Oleg Nenashev
 */
public class SSHLauncherWithJDKInstallerTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public DockerRule<SshdContainer> sshdContainer = new DockerRule<>(SshdContainer.class);

    @BeforeClass
    public static void checkFlag() {
        boolean enabled = Boolean.getBoolean(SSHLauncherWithJDKInstallerTest.class.getName() + ".enabled");
        Assume.assumeTrue("Test class is disabled: " + SSHLauncherWithJDKInstallerTest.class.getName(), enabled);
    }

    @Test
    @Issue("JENKINS-47448")
    public void shouldInstallJDKFromTheOracleWebsite() throws Exception {
        
        // Update the list of JDKs
        FormValidation res = JDKInstaller.JDKList.all().get(JDKInstaller.JDKList.class).updateNow();
        Assert.assertEquals("Failed to update JDK list. " + res.getMessage(), FormValidation.Kind.OK, res.kind);

        // Run container without JDK, let Jenkins to install the default JDK
        SshdContainer c = sshdContainer.get();
        DumbSlave slave = new DumbSlave("slave" + j.jenkins.getNodes().size(),
                "dummy", "/home/test/slave", "1", Node.Mode.NORMAL, "remote",
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
