package hudson.plugins.sshslaves;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;

import junit.framework.TestCase;

import org.junit.Assert;
import org.junit.Test;

public class SSHLauncherTest extends TestCase {

	@Test
	public void testCheckJavaVersionOpenJDK7NetBSD() {
		Assert.assertTrue("OpenJDK7 on NetBSD should be supported", checkSupported("openjdk-7-netbsd.version"));
	}

	@Test
	public void testCheckJavaVersionOpenJDK6Linux() {
		Assert.assertTrue("OpenJDK6 on Linux should be supported", checkSupported("openjdk-6-linux.version"));
	}

	@Test
	public void testCheckJavaVersionSun6Linux() {
		Assert.assertTrue("Sun 6 on Linux should be supported", checkSupported("sun-java-1.6-linux.version"));
	}

	@Test
	public void testCheckJavaVersionSun6Mac() {
		Assert.assertTrue("Sun 6 on Mac should be supported", checkSupported("sun-java-1.6-mac.version"));
	}

	@Test
	public void testCheckJavaVersionSun4Linux() {
		Assert.assertFalse("Sun 1.4 on Linux should NOT be supported", checkSupported("sun-java-1.4-linux.version"));
	}

	/**
	 * Returns true if the version is supported.
	 * 
	 * @param testVersionOutput
	 *            the resource to find relative to this class that contains the
	 *            output of "java -version"
	 * @return
	 */
	private static boolean checkSupported(final String testVersionOutput) {
		try {
			final String javaCommand = "testing-java";
			final InputStream versionStream = SSHLauncherTest.class
					.getResourceAsStream(testVersionOutput);
			final BufferedReader r = new BufferedReader(new InputStreamReader(
					versionStream));
			final StringWriter output = new StringWriter();
			final String result = SSHLauncher.checkJavaVersion(System.out,
					javaCommand, r, output);
			return null != result;
		} catch (final IOException e) {
			return false;
		}
	}
}
