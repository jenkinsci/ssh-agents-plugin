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
import java.util.Collections;

import hudson.model.Node.Mode;
import hudson.model.Slave;
import hudson.slaves.DumbSlave;
import hudson.slaves.RetentionStrategy;
import junit.framework.TestCase;

import org.junit.Assert;
import org.junit.Test;
import org.jvnet.hudson.test.HudsonTestCase;

public class SSHLauncherTest extends HudsonTestCase {

	@Test
	public void testCheckJavaVersionOpenJDK7NetBSD() throws Exception {
		Assert.assertTrue("OpenJDK7 on NetBSD should be supported", checkSupported("openjdk-7-netbsd.version"));
	}

	@Test
	public void testCheckJavaVersionOpenJDK6Linux() throws Exception {
		Assert.assertTrue("OpenJDK6 on Linux should be supported", checkSupported("openjdk-6-linux.version"));
	}

	@Test
	public void testCheckJavaVersionSun6Linux() throws Exception {
		Assert.assertTrue("Sun 6 on Linux should be supported", checkSupported("sun-java-1.6-linux.version"));
	}

	@Test
	public void testCheckJavaVersionSun6Mac() throws Exception {
		Assert.assertTrue("Sun 6 on Mac should be supported", checkSupported("sun-java-1.6-mac.version"));
	}

	@Test
	public void testCheckJavaVersionSun4Linux() {
        try {
		    checkSupported("sun-java-1.4-linux.version");
            fail();
        } catch (IOException e) {
            //
        }
	}

	/**
	 * Returns true if the version is supported.
	 * 
	 * @param testVersionOutput
	 *            the resource to find relative to this class that contains the
	 *            output of "java -version"
	 * @return
	 */
	private static boolean checkSupported(final String testVersionOutput) throws IOException {
        final String javaCommand = "testing-java";
        final InputStream versionStream = SSHLauncherTest.class
                .getResourceAsStream(testVersionOutput);
        final BufferedReader r = new BufferedReader(new InputStreamReader(
                versionStream));
        final StringWriter output = new StringWriter();
        final String result = new SSHLauncher(null,0,null,null,null,null,null, null, null).checkJavaVersion(System.out,
                javaCommand, r, output);
        return null != result;
	}

    public void testConfigurationRoundtrip() throws Exception {
        SSHLauncher launcher = new SSHLauncher("localhost", 123, "test", "pass", "xyz", "def",null, null, null);
        DumbSlave slave = new DumbSlave("slave", "dummy",
                createTmpDir().getPath(), "1", Mode.NORMAL, "",
                launcher, RetentionStrategy.NOOP, Collections.EMPTY_LIST);
        hudson.addNode(slave);

        submit(createWebClient().getPage(slave,"configure").getFormByName("config"));
        Slave n = (Slave)hudson.getNode("slave");

        assertNotSame(n,slave);
        assertNotSame(n.getLauncher(),launcher);
        assertEqualDataBoundBeans(n.getLauncher(),launcher);
    }
}
