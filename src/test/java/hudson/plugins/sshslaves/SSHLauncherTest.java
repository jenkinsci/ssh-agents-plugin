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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.test.acceptance.docker.DockerRule;
import org.jenkinsci.test.acceptance.docker.fixtures.JavaContainer;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import hudson.model.Descriptor;
import hudson.model.Fingerprint;
import hudson.model.JDK;
import hudson.model.Slave;
import hudson.plugins.sshslaves.verifiers.KnownHostsFileKeyVerificationStrategy;
import hudson.plugins.sshslaves.verifiers.NonVerifyingKeyVerificationStrategy;
import hudson.slaves.DumbSlave;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import hudson.slaves.SlaveComputer;
import hudson.tools.ToolLocationNodeProperty;
import hudson.util.FormValidation;
import hudson.util.VersionNumber;
import jenkins.model.Jenkins;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.HostnamePortSpecification;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import static hudson.plugins.sshslaves.SSHLauncher.JAR_CACHE_DIR;
import static hudson.plugins.sshslaves.SSHLauncher.JAR_CACHE_PARAM;
import static hudson.plugins.sshslaves.SSHLauncher.WORK_DIR_PARAM;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
    if (JavaProvider.getMinJavaLevel().equals(java7)) {
      assertTrue("OpenJDK7 on NetBSD should be supported", checkSupported("openjdk-7-netbsd.version"));
    } else {
      assertNotSupported("openjdk-7-netbsd.version");
    }
  }

  @Test
  public void checkJavaVersionOpenJDK6Linux() {
    assertNotSupported("openjdk-6-linux.version");
  }

  @Test
  public void checkJavaVersionSun6Linux() {
    assertNotSupported("sun-java-1.6-linux.version");
  }

  @Test
  public void checkJavaVersionSun6Mac() {
    assertNotSupported("sun-java-1.6-mac.version");
  }

  @Test
  public void testCheckJavaVersionOracle7Mac() throws Exception {
    VersionNumber java7 = new VersionNumber("7");
    if (JavaProvider.getMinJavaLevel().equals(java7)) {
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
  public void checkJavaVersionSun4Linux() {
    assertNotSupported("sun-java-1.4-linux.version");
  }

  /**
   * Returns true if the version is supported.
   *
   * @param testVersionOutput the resource to find relative to this class that contains the
   *                          output of "java -version"
   */
  private static boolean checkSupported(final String testVersionOutput) throws IOException {
    final String javaCommand = "testing-java";
    final InputStream versionStream = SSHLauncherTest.class
      .getResourceAsStream(testVersionOutput);
    final BufferedReader r = new BufferedReader(new InputStreamReader(
      versionStream));
    final StringWriter output = new StringWriter();
    final String result = new JavaVersionChecker(null, null, null, null)
      .checkJavaVersion(System.out, javaCommand, r, output);
    return null != result;
  }

  private static void assertNotSupported(final String testVersionOutput) throws AssertionError {
    assertThrows(IOException.class, () -> checkSupported(testVersionOutput));
  }

  private void checkRoundTrip(String host) throws Exception {
    SystemCredentialsProvider.getInstance().getDomainCredentialsMap().put(Domain.global(),
      Collections.singletonList(
        new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM, "dummyCredentialId", null, "user", "pass")
      )
    );
    SSHLauncher launcher = new SSHLauncher(host, 123, "dummyCredentialId");
    launcher.setSshHostKeyVerificationStrategy(new KnownHostsFileKeyVerificationStrategy());
    assertEquals(host.trim(), launcher.getHost());
    DumbSlave agent = new DumbSlave("agent", j.createTmpDir().getPath(), launcher);
    j.jenkins.addNode(agent);

    HtmlPage p = j.createWebClient().getPage(agent, "configure");
    j.submit(p.getFormByName("config"));
    Slave n = (Slave) j.jenkins.getNode("agent");

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
    SystemCredentialsProvider.getInstance().getDomainCredentialsMap().put(
      new Domain("test", null, Collections.singletonList(
        new HostnamePortSpecification(null, null)
      )),
      Collections.singletonList(
        new BasicSSHUserPrivateKey(CredentialsScope.SYSTEM, "dummyCredentialId", "john", null, null, null)
      )
    );

    SSHLauncher.DescriptorImpl desc = (SSHLauncher.DescriptorImpl) j.jenkins.getDescriptorOrDie(SSHLauncher.class);
    assertEquals(2, desc.doFillCredentialsIdItems(j.jenkins, "", "22", "does-not-exist").size());
    assertEquals(1, desc.doFillCredentialsIdItems(j.jenkins, "", "22", "dummyCredentialId").size());
    assertEquals(1, desc.doFillCredentialsIdItems(j.jenkins, "", "forty two", "does-not-exist").size());
    assertEquals(1, desc.doFillCredentialsIdItems(j.jenkins, "", "", "does-not-exist").size());
  }

  @Test
  public void checkJavaPathWhiteSpaces() {
    SSHLauncher.DescriptorImpl desc = (SSHLauncher.DescriptorImpl) j.jenkins.getDescriptorOrDie(SSHLauncher.class);
    assertEquals(FormValidation.ok(), desc.doCheckJavaPath("/usr/lib/jdk/bin/java"));
    assertEquals(FormValidation.ok(), desc.doCheckJavaPath("\"/usr/lib/jdk/bin/java\""));
    assertEquals(FormValidation.ok(), desc.doCheckJavaPath("'/usr/lib/jdk/bin/java'"));
    assertEquals(FormValidation.ok(), desc.doCheckJavaPath("\"/usr/lib/jdk 11/bin/java\""));
    assertEquals(FormValidation.ok(), desc.doCheckJavaPath("'/usr/lib/jdk 11/bin/java'"));
    assertEquals(FormValidation.Kind.WARNING, desc.doCheckJavaPath("/usr/lib/jdk 11/bin/java").kind);
  }

  @Test
  public void checkHost() {
    SSHLauncher.DescriptorImpl desc = (SSHLauncher.DescriptorImpl) j.jenkins.getDescriptorOrDie(SSHLauncher.class);
    assertEquals(FormValidation.ok(), desc.doCheckHost("hostname"));
    assertEquals(FormValidation.Kind.ERROR, desc.doCheckHost("").kind);
    assertEquals(FormValidation.Kind.ERROR, desc.doCheckHost(null).kind);
  }

  @Test
  public void checkPort() {
    SSHLauncher.DescriptorImpl desc = (SSHLauncher.DescriptorImpl) j.jenkins.getDescriptorOrDie(SSHLauncher.class);
    assertEquals(FormValidation.ok(), desc.doCheckPort("22"));
    assertEquals(FormValidation.Kind.ERROR, desc.doCheckPort("").kind);
    assertEquals(FormValidation.Kind.ERROR, desc.doCheckPort(null).kind);
    assertEquals(FormValidation.Kind.ERROR, desc.doCheckPort("-1").kind);
    assertEquals(FormValidation.Kind.ERROR, desc.doCheckPort("65536").kind);
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
    SystemCredentialsProvider.getInstance().getDomainCredentialsMap().put(Domain.global(),
      Collections.singletonList(
        credentials
      )
    );
    SSHLauncher launcher = new SSHLauncher("localhost", 123, "dummyCredentialId");
    launcher.setLaunchTimeoutSeconds(5);
    launcher.setRetryWaitTime(5);
    launcher.setMaxNumRetries(2);
    DumbSlave agent = new DumbSlave("agent", j.createTmpDir().getPath(), launcher);

    Fingerprint fingerprint = CredentialsProvider.getFingerprintOf(credentials);
    assertThat("No fingerprint created until use", fingerprint, nullValue());

    j.jenkins.addNode(agent);
    while (agent.toComputer().isConnecting()) {
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
    SystemCredentialsProvider.getInstance().getDomainCredentialsMap().put(Domain.global(),
      Collections.singletonList(
        credentials
      )
    );
    SSHLauncher launcher = new SSHLauncher("localhost", 123, "dummyCredentialId");
    launcher.setLaunchTimeoutSeconds(5);
    launcher.setRetryWaitTime(5);
    launcher.setMaxNumRetries(2);
    DumbSlave agent = new DumbSlave("agent", j.createTmpDir().getPath(), launcher);

    Fingerprint fingerprint = CredentialsProvider.getFingerprintOf(credentials);
    assertThat("No fingerprint created until use", fingerprint, nullValue());

    j.jenkins.addNode(agent);
    while (agent.toComputer().isConnecting()) {
      // Make sure verification takes place after launch is complete
      Thread.sleep(100);
    }

    fingerprint = CredentialsProvider.getFingerprintOf(credentials);
    assertThat(fingerprint, notNullValue());
  }

  @Issue("JENKINS-44111")
  @Test
  public void workDirTest() {
    String rootFS = "/home/user";
    String anotherWorkDir = "/another/workdir";

    SSHLauncher launcher = new SSHLauncher("Hostname", 22, "credentialID", "jvmOptions",
      "javaPath", "prefix", "suffix",
      60, 10, 15, new NonVerifyingKeyVerificationStrategy());
    //use rootFS
    Assert.assertEquals(launcher.getWorkDirParam(rootFS), WORK_DIR_PARAM + rootFS + JAR_CACHE_PARAM + rootFS + JAR_CACHE_DIR);

    launcher = new SSHLauncher("Hostname", 22, "credentialID", "jvmOptions",
      "javaPath", "prefix", "suffix" + WORK_DIR_PARAM + anotherWorkDir,
      60, 10, 15, new NonVerifyingKeyVerificationStrategy());
    //if worDir is in suffix return ""
    Assert.assertEquals(launcher.getWorkDirParam(rootFS), "");
    //if worDir is in suffix return "", even do you set workDir in configuration
    launcher.setWorkDir(anotherWorkDir);
    Assert.assertEquals(launcher.getWorkDirParam(rootFS), "");

    launcher = new SSHLauncher("Hostname", 22, "credentialID", "jvmOptions",
      "javaPath", "prefix", "suffix",
      60, 10, 15, new NonVerifyingKeyVerificationStrategy());
    //user the workDir set in configuration
    launcher.setWorkDir(anotherWorkDir);
    Assert.assertEquals(launcher.getWorkDirParam(rootFS), WORK_DIR_PARAM + anotherWorkDir + JAR_CACHE_PARAM + anotherWorkDir + JAR_CACHE_DIR);
  }

  @Issue("JENKINS-53245")
  @Test
  public void setJavaHome() throws Exception {
    String javaHome = "/java_home";
    String javaHomeTool = "/java_home_tool";

    SSHLauncher sshLauncher = new SSHLauncher("Hostname", 22, "credentialID");
    DumbSlave agent = new DumbSlave("agent" + j.jenkins.getNodes().size(), "/home/test/agent", sshLauncher);
    j.jenkins.addNode(agent);
    SlaveComputer computer = agent.getComputer();

    List<EnvironmentVariablesNodeProperty.Entry> env = new ArrayList<>();
    EnvironmentVariablesNodeProperty.Entry entry = new EnvironmentVariablesNodeProperty.Entry(DefaultJavaProvider.JAVA_HOME, javaHome);
    env.add(entry);
    NodeProperty<?> javaHomeProperty = new EnvironmentVariablesNodeProperty(env);

    JDK.DescriptorImpl jdkType = Jenkins.get().getDescriptorByType(JDK.DescriptorImpl.class);
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
    assertTrue(javas.contains(SSHLauncher.getWorkingDirectory(computer) + DefaultJavaProvider.JDK_BIN_JAVA));
  }

  @Test
  public void timeoutAndRetrySettings() {
    final SSHLauncher launcher = new SSHLauncher("Hostname", 22, "credentialID", "jvmOptions",
      "javaPath", "prefix", "suffix",
      39, 18, 25,
      new NonVerifyingKeyVerificationStrategy());
    assertEquals(39, launcher.getLaunchTimeoutSeconds().intValue());
    assertEquals(18, launcher.getMaxNumRetries().intValue());
    assertEquals(25, launcher.getRetryWaitTime().intValue());
  }

  @Issue("JENKINS-54934")
  @Test
  public void timeoutAndRetrySettingsAllowZero() {
    final SSHLauncher launcher = new SSHLauncher("Hostname", 22, "credentialID", "jvmOptions",
      "javaPath", "prefix", "suffix",
      0, 0, 0,
      new NonVerifyingKeyVerificationStrategy());
    assertEquals(0, launcher.getMaxNumRetries().intValue());
    assertEquals(0, launcher.getRetryWaitTime().intValue());
  }

  @Test
  public void timeoutAndRetrySettingsSetDefaultsIfOutOfRange() {
    final SSHLauncher launcher = new SSHLauncher("Hostname", 22, "credentialID", "jvmOptions",
      "javaPath", "prefix", "suffix",
      0, -1, -1,
      new NonVerifyingKeyVerificationStrategy());
    assertEquals(SSHLauncher.DEFAULT_LAUNCH_TIMEOUT_SECONDS, launcher.getLaunchTimeoutSeconds());
    assertEquals(SSHLauncher.DEFAULT_MAX_NUM_RETRIES, launcher.getMaxNumRetries());
    assertEquals(SSHLauncher.DEFAULT_RETRY_WAIT_TIME, launcher.getRetryWaitTime());

    final SSHLauncher launcher2 = new SSHLauncher("Hostname", 22, "credentialID", "jvmOptions",
      "javaPath", "prefix", "suffix",
      null, null, null,
      new NonVerifyingKeyVerificationStrategy());
    assertEquals(SSHLauncher.DEFAULT_LAUNCH_TIMEOUT_SECONDS, launcher2.getLaunchTimeoutSeconds());
    assertEquals(SSHLauncher.DEFAULT_MAX_NUM_RETRIES, launcher2.getMaxNumRetries());
    assertEquals(SSHLauncher.DEFAULT_RETRY_WAIT_TIME, launcher2.getRetryWaitTime());
  }

  @Test
  public void getMd5Hash() {

    try {
      byte[] bytes = "Leave me alone!".getBytes();
      String result = SSHLauncher.getMd5Hash(bytes);
      assertEquals("1EB226C8E950BAC1494BE197E84A264C", result);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  public void readInputStreamIntoByteArrayAndClose() {

    InputStream inputStream = null;
    File testFile = null;
    try {

      testFile = new File("target" + File.separator + "test-classes",
        "readInputStreamIntoByteArrayTestFile.txt");
      assertTrue(testFile.exists());
      inputStream = new FileInputStream(testFile);
      byte[] bytes = SSHLauncher.readInputStreamIntoByteArrayAndClose(inputStream);
      assertNotNull(bytes);
      assertTrue(bytes.length > 0);
      assertEquals("Don't change me or add newlines!", new String(bytes));

    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      IOUtils.closeQuietly(inputStream);
    }
  }

  @Test
  public void retryTest() throws IOException, InterruptedException, Descriptor.FormException {
    DumbSlave agent = getPermanentAgentHostNotExist();
    j.jenkins.addNode(agent);
    String log = "";
    for(int i=0; i<60; i++){
      Thread.sleep(1000);
      log = agent.getComputer().getLog();
      if(log.contains("There are 1 more retries left.")){
        break;
      }
    }
    assertTrue(log.contains("There are 3 more retries left."));
    assertTrue(log.contains("There are 2 more retries left."));
    assertTrue(log.contains("There are 1 more retries left."));
    assertFalse(log.contains("There are 4 more retries left."));
  }

  private DumbSlave getPermanentAgentHostNotExist() throws Descriptor.FormException, IOException {
    fakeCredentials("dummyCredentialId");
    final SSHLauncher launcher = new SSHLauncher("HostNotExists", 22, "dummyCredentialId");
    launcher.setSshHostKeyVerificationStrategy(new NonVerifyingKeyVerificationStrategy());
    launcher.setLaunchTimeoutSeconds(5);
    launcher.setRetryWaitTime(1);
    launcher.setMaxNumRetries(3);
    return new DumbSlave("agent", j.createTmpDir().getPath(), launcher);
  }

  private void fakeCredentials(String id) {
    BasicSSHUserPrivateKey credentials = new BasicSSHUserPrivateKey(CredentialsScope.SYSTEM, id, "user", null, "", "desc");
    SystemCredentialsProvider.getInstance().getDomainCredentialsMap().put(Domain.global(),
      Collections.singletonList(
        credentials
      )
    );
  }

  @Test
  public void KnownHostsFileDefaultConfig(){
    String defaultPath = Paths.get(System.getProperty("user.home"), ".ssh", "known_hosts").toString();
    KnownHostsFileKeyVerificationStrategy khvs = new KnownHostsFileKeyVerificationStrategy();
    assertEquals(khvs.getKnownHostsFile().getPath(),defaultPath);
  }
}
