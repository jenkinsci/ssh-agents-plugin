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
package io.jenkins.plugins.sshbuildagents.ssh.mina;

import static hudson.Util.fixEmpty;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.util.io.output.NoCloseOutputStream;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.domains.HostnamePortRequirement;
import com.cloudbees.plugins.credentials.domains.SchemeRequirement;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.plugins.sshslaves.SSHConnector;
import hudson.plugins.sshslaves.verifiers.HostKey;
import hudson.plugins.sshslaves.verifiers.NonVerifyingKeyVerificationStrategy;
import hudson.plugins.sshslaves.verifiers.SshHostKeyVerificationStrategy;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.SlaveComputer;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.sshbuildagents.Messages;
import io.jenkins.plugins.sshbuildagents.ssh.Connection;
import io.jenkins.plugins.sshbuildagents.ssh.ServerHostKeyVerifier;
import io.jenkins.plugins.sshbuildagents.ssh.ShellChannel;
import jenkins.model.Jenkins;

/**
 * A computer launcher that tries to start a linux agent by opening an SSH
 * connection and trying to find java.
 *
 * @author Ivan Fernandez Calvo
 */
public class SSHApacheMinaLauncher extends ComputerLauncher {
  /**
   * The scheme requirement.
   */
  public static final SchemeRequirement SSH_SCHEME = new SchemeRequirement("ssh");
  public static final Integer DEFAULT_MAX_NUM_RETRIES = 10;
  public static final Integer DEFAULT_RETRY_WAIT_TIME = 15;
  public static final Integer DEFAULT_LAUNCH_TIMEOUT_SECONDS = 60;
  public static final String AGENT_JAR = "remoting.jar";
  public static final String SLASH_AGENT_JAR = "/" + AGENT_JAR;
  public static final String WORK_DIR_PARAM = " -workDir ";
  public static final String JAR_CACHE_PARAM = " -jar-cache ";
  public static final String JAR_CACHE_DIR = "/remoting/jarCache";
  public static final int DEFAULT_SSH_PORT = 22;
  private static final Logger LOGGER = Logger.getLogger(SSHApacheMinaLauncher.class.getName());
  /**
   * Field javaPath.
   */
  public String javaPath;
  /**
   * Field prefixStartSlaveCmd.
   */
  public String prefixStartSlaveCmd;
  /**
   * Field suffixStartSlaveCmd.
   */
  public String suffixStartSlaveCmd;
  /**
   * Field launchTimeoutSeconds.
   */
  public Integer launchTimeoutSeconds;
  /**
   * Field maxNumRetries.
   */
  public Integer maxNumRetries;
  /**
   * Field retryWaitTime (seconds).
   */
  public Integer retryWaitTime;
  /**
   * Field host
   */
  private String host;
  /**
   * Field port
   */
  private int port;
  /**
   * The id of the credentials to use.
   */
  private final String credentialsId;
  /**
   * Transient stash of the credentials to use, mostly just for providing floating
   * user object.
   */
  private transient StandardUsernameCredentials credentials;
  /**
   * Field jvmOptions.
   */
  private String jvmOptions;
  /**
   * SSH connection to the agent.
   */
  private transient volatile Connection connection;

  /**
   * The verifier to use for checking the SSH key presented by the host
   * responding to the connection
   */
  @CheckForNull
  private SshHostKeyVerificationStrategy sshHostKeyVerificationStrategy;

  /**
   * Allow to enable/disable the TCP_NODELAY flag on the SSH connection.
   */
  private Boolean tcpNoDelay;

  /**
   * Set the value to add to the remoting parameter -workDir
   *
   * @see <a href=
   *      "https://github.com/jenkinsci/remoting/blob/master/docs/workDir.md#remoting-work-directory">Remoting
   *      Work directory</a>
   */
  private String workDir;

  /**
   * Shell channel to execute the remoting process.
   */
  @CheckForNull
  private transient ShellChannel shellChannel;

  /**
   * Constructor SSHLauncher creates a new SSHLauncher instance.
   *
   * @param host          The host to connect to.
   * @param port          The port to connect on.
   * @param credentialsId The credentials id to connect as.
   */
  @DataBoundConstructor
  public SSHApacheMinaLauncher(@NonNull String host, int port, String credentialsId) {
    setHost(host);
    setPort(port);
    this.credentialsId = credentialsId;

    this.launchTimeoutSeconds = DEFAULT_LAUNCH_TIMEOUT_SECONDS;
    this.maxNumRetries = DEFAULT_MAX_NUM_RETRIES;
    this.retryWaitTime = DEFAULT_RETRY_WAIT_TIME;
  }

  public static StandardUsernameCredentials lookupSystemCredentials(String credentialsId) {
    return CredentialsMatchers.firstOrNull(
        CredentialsProvider
            .lookupCredentialsInItemGroup(StandardUsernameCredentials.class, Jenkins.get(), ACL.SYSTEM2,
                List.of(SSH_SCHEME)),
        CredentialsMatchers.withId(credentialsId));
  }

  /**
   * Gets the formatted current time stamp.
   *
   * @return the formatted current time stamp.
   */
  @Restricted(NoExternalUse.class)
  public static String getTimestamp() {
    return String.format("[%1$tD %1$tT]", new Date());
  }

  /**
   * Returns the remote root workspace (without trailing slash).
   *
   * @param computer The computer to get the root workspace of.
   * @return the remote root workspace (without trailing slash).
   */
  @CheckForNull
  public static String getWorkingDirectory(SlaveComputer computer) {
    return getWorkingDirectory(computer.getNode());
  }

  @CheckForNull
  private static String getWorkingDirectory(@CheckForNull Slave agent) {
    if (agent == null) {
      return null;
    }
    String workingDirectory = agent.getRemoteFS();
    while (workingDirectory.endsWith("/")) {
      workingDirectory = workingDirectory.substring(0, workingDirectory.length() - 1);
    }
    return workingDirectory;
  }

  public StandardUsernameCredentials getCredentials() {
    String credentialsId = this.credentialsId == null ? (this.credentials == null ? null : this.credentials.getId())
        : this.credentialsId;
    try {
      // only ever want from the system
      // lookup every time so that we always have the latest
      StandardUsernameCredentials credentials = credentialsId != null ? SSHApacheMinaLauncher.lookupSystemCredentials(
          credentialsId) : null;
      if (credentials != null) {
        this.credentials = credentials;
        return credentials;
      }
    } catch (Throwable t) {
      // ignore
    }

    return this.credentials;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isLaunchSupported() {
    return connection == null;
  }

  /**
   * Gets the optional JVM Options used to launch the agent JVM.
   *
   * @return The optional JVM Options used to launch the agent JVM.
   */
  public String getJvmOptions() {
    return jvmOptions == null ? "" : jvmOptions;
  }

  @DataBoundSetter
  public void setJvmOptions(String value) {
    this.jvmOptions = fixEmpty(value);
  }

  /**
   * Gets the optional java command to use to launch the agent JVM.
   *
   * @return The optional java command to use to launch the agent JVM.
   */
  public String getJavaPath() {
    return javaPath == null ? "" : javaPath;
  }

  @DataBoundSetter
  public void setJavaPath(String value) {
    this.javaPath = fixEmpty(value);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void launch(final SlaveComputer computer, final TaskListener listener) throws InterruptedException {
    final Node node = computer.getNode();
    final String host = this.host;
    final int port = this.port;
    checkConfig();
    synchronized (this) {
      if (connection != null) {
        listener.getLogger().println(Messages.SSHLauncher_alreadyConnected());
        return;
      }
      connection = new ConnectionImpl(host, port);

      final String workingDirectory = getWorkingDirectory(computer);
      listener.getLogger().println(logConfiguration());
      try {
        openConnection(listener, computer, workingDirectory);
        copyAgentJar(listener, workingDirectory);
        verifyNoHeaderJunk(listener);
        reportEnvironment(listener);
        startAgent(computer, listener, workingDirectory);
      } catch (Error | Exception e) {
        String msg = Messages.SSHLauncher_UnexpectedError();
        if (StringUtils.isNotBlank(e.getMessage())) {
          msg = e.getMessage();
        }
        e.printStackTrace(listener.error(msg));
        close();
      }
    }
    if (node != null && getTrackCredentials()) {
      CredentialsProvider.track(node, getCredentials());
    }
  }

  private String expandExpression(SlaveComputer computer, String expression) {
    return getEnvVars(computer).expand(expression);
  }

  private EnvVars getEnvVars(SlaveComputer computer) {
    final EnvVars global = getEnvVars(Jenkins.get());

    final Node node = computer.getNode();
    final EnvVars local = node != null ? getEnvVars(node) : null;

    if (global != null) {
      if (local != null) {
        final EnvVars merged = new EnvVars(global);
        merged.overrideAll(local);

        return merged;
      } else {
        return global;
      }
    } else if (local != null) {
      return local;
    } else {
      return new EnvVars();
    }
  }

  private EnvVars getEnvVars(Jenkins h) {
    return getEnvVars(h.getGlobalNodeProperties());
  }

  private EnvVars getEnvVars(Node n) {
    return getEnvVars(n.getNodeProperties());
  }

  private EnvVars getEnvVars(DescribableList<NodeProperty<?>, NodePropertyDescriptor> dl) {
    final EnvironmentVariablesNodeProperty evnp = dl.get(EnvironmentVariablesNodeProperty.class);
    if (evnp == null) {
      return null;
    }

    return evnp.getEnvVars();
  }

  /**
   * Makes sure that SSH connection won't produce any unwanted text, which will
   * interfere with sftp execution.
   * TODO review if it is needed or move to the SSH Provider.
   */
  private void verifyNoHeaderJunk(TaskListener listener) throws IOException, InterruptedException {

    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      connection.execCommand("exit 0");
      final String s;
      // TODO: Seems we need to retrieve the encoding from the connection destination
      s = baos.toString(Charset.defaultCharset().name());
      if (s.length() != 0) {
        listener.getLogger().println(Messages.SSHLauncher_SSHHeaderJunkDetected());
        listener.getLogger().println(s);
        throw new AbortException();
      }
    } catch (UnsupportedEncodingException ex) { // Should not happen
      throw new IOException("Default encoding is unsupported", ex);
    }
  }

  /**
   * Starts the agent process.
   *
   * @param computer         The computer.
   * @param listener         The listener.
   * @param workingDirectory The working directory from which to start the java
   *                         process.
   * @throws IOException If something goes wrong.
   */
  private void startAgent(SlaveComputer computer, final TaskListener listener, String workingDirectory)
      throws IOException {
    String java = "java";
    if (StringUtils.isNotBlank(javaPath)) {
      java = expandExpression(computer, javaPath);
    }

    String cmd = "cd \"" + workingDirectory + "\" && " + java + " " + getJvmOptions() + " -jar " + AGENT_JAR
        + getWorkDirParam(
            workingDirectory);

    // This will wrap the cmd with prefix commands and suffix commands if they are
    // set.
    cmd = getPrefixStartSlaveCmd() + cmd + getSuffixStartSlaveCmd();

    listener.getLogger().println(Messages.SSHLauncher_StartingAgentProcess(getTimestamp(), cmd));
    shellChannel = connection.shellChannel();
    shellChannel.execCommand(cmd);
    try {
      computer.setChannel(shellChannel.getInvertedStdout(), shellChannel.getInvertedStdin(), listener.getLogger(),
          null);
    } catch (InterruptedException e) {
      throw new IOException(Messages.SSHLauncher_AbortedDuringConnectionOpen(), e);
    } catch (IOException e) {
      throw new AbortException(e.getMessage());
    }
  }

  /**
   * Method copies the agent jar to the remote system.
   *
   * @param listener         The listener.
   * @param workingDirectory The directory into which the agent jar will be
   *                         copied.
   * @throws IOException If something goes wrong.
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE", justification = "there is a bug related with Java 11 bytecode see https://github.com/spotbugs/spotbugs/issues/756")
  private void copyAgentJar(TaskListener listener, String workingDirectory) throws IOException {
    String fileName = workingDirectory + SLASH_AGENT_JAR;
    boolean overwrite = true;
    boolean checkSameContent = true;
    byte[] bytes = new Slave.JnlpJar(AGENT_JAR).readFully();
    try {
      listener.getLogger().println("Uploading " + fileName + " file to the agent.");
      connection.copyFile(fileName, bytes, overwrite, checkSameContent);
    } catch (Exception e) {
      listener.getLogger().println("Error: unable to write the " + fileName + " file to the agent.");
      listener.getLogger().println("Check the user, work directory, and permissions you have configured.");
      throw new IOException(e);
    }
  }

  protected void reportEnvironment(TaskListener listener) throws IOException {
    listener.getLogger().println(Messages._SSHLauncher_RemoteUserEnvironment(getTimestamp()));
    connection.execCommand("set");
  }

  protected void openConnection(final TaskListener listener, final SlaveComputer computer,
      final String workingDirectory) throws IOException {
    if (StringUtils.isBlank(workingDirectory)) {
      String msg = "Cannot get the working directory for " + computer;
      listener.error(msg);
      throw new AbortException(msg);
    }
    StandardUsernameCredentials credentials = getCredentials();
    if (credentials == null) {
      throw new AbortException("Cannot find SSH User credentials with id: " + credentialsId);
    }
    // TODO implement verifiers.
    String[] preferredKeyAlgorithms = getSshHostKeyVerificationStrategyDefaulted().getPreferredKeyAlgorithms(computer);
    if (preferredKeyAlgorithms != null && preferredKeyAlgorithms.length > 0) { // JENKINS-44832
      connection.setServerHostKeyAlgorithms(preferredKeyAlgorithms);
    } else {
      listener.getLogger().println("Warning: no key algorithms provided; JENKINS-42959 disabled");
    }
    PrintStream logger = listener.getLogger();
    logger.println(Messages.SSHLauncher_OpeningSSHConnection(getTimestamp(), host + ":" + port));
    connection.setTCPNoDelay(getTcpNoDelay());
    connection.setServerHostKeyVerifier(new ServerHostKeyVerifierImpl(computer, listener));
    connection.setTimeout((int) getLaunchTimeoutMillis());
    connection.setCredentials(credentials);
    connection.setRetries(getMaxNumRetries());
    connection.setRetryWaitTime(getRetryWaitTime());
    connection.setWorkingDirectory(workingDirectory);
    connection.setStdErr(new NoCloseOutputStream(listener.getLogger()));
    connection.setStdOut(new NoCloseOutputStream(listener.getLogger()));
    connection.connect();
  }

  private void checkConfig() throws InterruptedException {
    // JENKINS-58340 some plugins does not implement Descriptor
    Descriptor descriptorOrg = Jenkins.get().getDescriptor(this.getClass());
    if (!(descriptorOrg instanceof DescriptorImpl)) {
      return;
    }

    DescriptorImpl descriptor = (DescriptorImpl) descriptorOrg;
    String message = "Validate configuration:\n";
    boolean isValid = true;

    String port = String.valueOf(this.port);
    FormValidation validatePort = descriptor.doCheckPort(port);
    FormValidation validateHost = descriptor.doCheckHost(this.host);
    FormValidation validateCredentials = descriptor.doCheckCredentialsId(Jenkins.get(), Jenkins.get(), this.host, port,
        this.credentialsId);

    if (validatePort.kind == FormValidation.Kind.ERROR) {
      isValid = false;
      message += validatePort.getMessage() + "\n";
    }
    if (validateHost.kind == FormValidation.Kind.ERROR) {
      isValid = false;
      message += validateHost.getMessage() + "\n";
    }
    if (validateCredentials.kind == FormValidation.Kind.ERROR) {
      isValid = false;
      message += validateCredentials.getMessage() + "\n";
    }

    if (!isValid) {
      throw new InterruptedException(message);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void afterDisconnect(SlaveComputer slaveComputer, final TaskListener listener) {
    if (connection == null) {
      // Nothing to do here, the connection is not established
      return;
    } else {
      try {
        if (shellChannel != null) {
          if (shellChannel.getLastError() != null) {
            listener.getLogger().println("\tException: " + shellChannel.getLastError().getMessage());
          }
          if (StringUtils.isNotBlank(shellChannel.getLastHint())) {
            listener.getLogger().println("\tHint: " + shellChannel.getLastHint());
          }
        }
        close();
      } catch (Exception e) {
        listener.getLogger().println("Error after disconnect agent: " + e.getMessage());
      }
    }
  }

  private void close() {
    try {
      if (shellChannel != null) {
        shellChannel.close();
      }
      if (connection != null) {
        connection.close();
      }
    } catch (Exception e) {
      // NOOP
    }
    connection = null;
    shellChannel = null;
  }

  public String getCredentialsId() {
    return credentialsId;
  }

  @CheckForNull
  public SshHostKeyVerificationStrategy getSshHostKeyVerificationStrategy() {
    return sshHostKeyVerificationStrategy;
  }

  @DataBoundSetter
  public void setSshHostKeyVerificationStrategy(SshHostKeyVerificationStrategy value) {
    this.sshHostKeyVerificationStrategy = value;
  }

  @NonNull
  SshHostKeyVerificationStrategy getSshHostKeyVerificationStrategyDefaulted() {
    return sshHostKeyVerificationStrategy != null
        ? sshHostKeyVerificationStrategy
        : new NonVerifyingKeyVerificationStrategy();
  }

  /**
   * Getter for property 'host'.
   *
   * @return Value for property 'host'.
   */
  public String getHost() {
    return host;
  }

  public void setHost(String value) {
    this.host = Util.fixEmptyAndTrim(value);
  }

  /**
   * Getter for property 'port'.
   *
   * @return Value for property 'port'.
   */
  public int getPort() {
    return port;
  }

  public void setPort(int value) {
    this.port = value == 0 ? DEFAULT_SSH_PORT : value;
  }

  public Connection getConnection() {
    return connection;
  }

  @NonNull
  public String getPrefixStartSlaveCmd() {
    return Util.fixNull(prefixStartSlaveCmd);
  }

  @DataBoundSetter
  public void setPrefixStartSlaveCmd(String value) {
    this.prefixStartSlaveCmd = fixEmpty(value);
  }

  @NonNull
  public String getSuffixStartSlaveCmd() {
    return Util.fixNull(suffixStartSlaveCmd);
  }

  @DataBoundSetter
  public void setSuffixStartSlaveCmd(String value) {
    this.suffixStartSlaveCmd = fixEmpty(value);
  }

  /**
   * Getter for property 'launchTimeoutSeconds'
   *
   * @return launchTimeoutSeconds
   */
  @NonNull
  public Integer getLaunchTimeoutSeconds() {
    return launchTimeoutSeconds;
  }

  @DataBoundSetter
  public void setLaunchTimeoutSeconds(Integer value) {
    this.launchTimeoutSeconds = value == null || value <= 0 ? DEFAULT_LAUNCH_TIMEOUT_SECONDS : value;
  }

  private long getLaunchTimeoutMillis() {
    return launchTimeoutSeconds == null || launchTimeoutSeconds < 0
        ? DEFAULT_LAUNCH_TIMEOUT_SECONDS
        : TimeUnit.SECONDS.toMillis(launchTimeoutSeconds);
  }

  /**
   * Getter for property 'maxNumRetries'
   *
   * @return maxNumRetries
   */
  @NonNull
  public Integer getMaxNumRetries() {
    return maxNumRetries == null || maxNumRetries < 0 ? DEFAULT_MAX_NUM_RETRIES : maxNumRetries;
  }

  @DataBoundSetter
  public void setMaxNumRetries(Integer value) {
    this.maxNumRetries = value != null && value >= 0 ? value : DEFAULT_MAX_NUM_RETRIES;
  }

  /**
   * Getter for property 'retryWaitTime'
   *
   * @return retryWaitTime
   */
  @NonNull
  public Integer getRetryWaitTime() {
    return retryWaitTime == null || retryWaitTime < 0 ? DEFAULT_RETRY_WAIT_TIME : retryWaitTime;
  }

  @DataBoundSetter
  public void setRetryWaitTime(Integer value) {
    this.retryWaitTime = value != null && value >= 0 ? value : DEFAULT_RETRY_WAIT_TIME;
  }

  public boolean getTcpNoDelay() {
    return tcpNoDelay != null ? tcpNoDelay : true;
  }

  @DataBoundSetter
  public void setTcpNoDelay(boolean tcpNoDelay) {
    this.tcpNoDelay = tcpNoDelay;
  }

  /**
   * Enable/Disable the credential tracking, this tracking store information about
   * where it is used a credential,
   * in this case in a node. If the tracking is enabled and you launch a big
   * number of Agents per day, activate
   * credentials tacking could cause a performance issue see
   *
   * @see <a href=
   *      "https://issues.jenkins-ci.org/browse/JENKINS-49235">JENKINS-49235</a>
   */
  public boolean getTrackCredentials() {
    String trackCredentials = System.getProperty(SSHApacheMinaLauncher.class.getName() + ".trackCredentials");
    return !"false".equalsIgnoreCase(trackCredentials);
  }

  public String getWorkDir() {
    return workDir;
  }

  @DataBoundSetter
  public void setWorkDir(String workDir) {
    this.workDir = Util.fixEmptyAndTrim(workDir);
  }

  /**
   * @param workingDirectory The Working directory set on the configuration of the
   *                         node.
   * @return the remoting parameter to set the workDir,
   *         by default it is the same as the working directory configured on the
   *         node so "-workDir " + workingDirectory,
   *         if workDir is set, he method will return "-workDir " + getWorkDir()
   *         if the parameter is set in suffixStartSlaveCmd, the method will
   *         return an empty String.
   */
  @NonNull
  @Restricted(NoExternalUse.class)
  public String getWorkDirParam(@NonNull String workingDirectory) {
    String ret;
    if (getSuffixStartSlaveCmd().contains(WORK_DIR_PARAM) || getSuffixStartSlaveCmd().contains(JAR_CACHE_PARAM)) {
      // the parameter is already set on suffixStartSlaveCmd
      ret = "";
    } else if (StringUtils.isNotBlank(getWorkDir())) {
      ret = WORK_DIR_PARAM + getWorkDir() + JAR_CACHE_PARAM + getWorkDir() + JAR_CACHE_DIR;
    } else {
      ret = WORK_DIR_PARAM + workingDirectory + JAR_CACHE_PARAM + workingDirectory + JAR_CACHE_DIR;
    }
    return ret;
  }

  public String logConfiguration() {
    final StringBuilder sb = new StringBuilder(this.getClass().getName() + "{");
    sb.append("host='").append(getHost()).append('\'');
    sb.append(", port=").append(getPort());
    sb.append(", credentialsId='").append(Util.fixNull(credentialsId)).append('\'');
    sb.append(", jvmOptions='").append(getJvmOptions()).append('\'');
    sb.append(", javaPath='").append(Util.fixNull(javaPath)).append('\'');
    sb.append(", prefixStartSlaveCmd='").append(getPrefixStartSlaveCmd()).append('\'');
    sb.append(", suffixStartSlaveCmd='").append(getSuffixStartSlaveCmd()).append('\'');
    sb.append(", launchTimeoutSeconds=").append(getLaunchTimeoutSeconds());
    sb.append(", maxNumRetries=").append(getMaxNumRetries());
    sb.append(", retryWaitTime=").append(getRetryWaitTime());
    sb.append(", sshHostKeyVerificationStrategy=").append(
        sshHostKeyVerificationStrategy != null ? sshHostKeyVerificationStrategy.getClass().getName() : "None");
    sb.append(", tcpNoDelay=").append(getTcpNoDelay());
    sb.append(", trackCredentials=").append(getTrackCredentials());
    sb.append('}');
    return sb.toString();
  }

  @Extension
  @Symbol({ "ssh", "sshMinaLauncher" })
  public static class DescriptorImpl extends Descriptor<ComputerLauncher> {

    /**
     * {@inheritDoc}
     */
    public String getDisplayName() {
      return Messages.SSHApacheMinaLauncher_DescriptorDisplayName();
    }

    public Class getSshConnectorClass() {
      return SSHConnector.class;
    }

    /**
     * Delegates the help link to the {@link SSHConnector}.
     */
    @Override
    public String getHelpFile(String fieldName) {
      String n = super.getHelpFile(fieldName);
      if (n == null)
        n = Jenkins.get().getDescriptorOrDie(SSHConnector.class).getHelpFile(fieldName);
      return n;
    }

    @RequirePOST
    public ListBoxModel doFillCredentialsIdItems(@AncestorInPath AccessControlled context,
        @QueryParameter String host,
        @QueryParameter String port,
        @QueryParameter String credentialsId) {
      Jenkins jenkins = Jenkins.get();
      if ((context == jenkins && !jenkins.hasPermission(Computer.CREATE))
          || (context != jenkins && !context.hasPermission(Computer.CONFIGURE))) {
        return new StandardUsernameListBoxModel()
            .includeCurrentValue(credentialsId);
      }
      try {
        int portValue = Integer.parseInt(port);
        // TODO review if the HostnamePortRequirement is really needed
        return new StandardUsernameListBoxModel()
            .includeMatchingAs(
                ACL.SYSTEM2,
                jenkins,
                StandardUsernameCredentials.class,
                // Collections.singletonList(SSH_SCHEME),
                Collections.singletonList(
                    new HostnamePortRequirement(host, portValue)),
                SSHAuthenticator.matcher(ClientSession.class))
            .includeCurrentValue(credentialsId); // always add the current value last in case already present
      } catch (NumberFormatException ex) {
        return new StandardUsernameListBoxModel()
            .includeCurrentValue(credentialsId);
      }
    }

    @RequirePOST
    public FormValidation doCheckCredentialsId(@AncestorInPath ItemGroup context,
        @AncestorInPath AccessControlled _context,
        @QueryParameter String host,
        @QueryParameter String port,
        @QueryParameter String value) {
      Jenkins jenkins = Jenkins.get();
      if ((_context == jenkins && !jenkins.hasPermission(Computer.CREATE))
          || (_context != jenkins && !_context.hasPermission(Computer.CONFIGURE))) {
        return FormValidation.ok(); // no need to alarm a user that cannot configure
      }
      try {
        // TODO review if the HostnamePortRequirement is really needed
        int portValue = Integer.parseInt(port);
        for (ListBoxModel.Option o : CredentialsProvider
            .listCredentialsInItemGroup(StandardUsernameCredentials.class, context, ACL.SYSTEM2,
                // Collections.singletonList(SSH_SCHEME),
                Collections.singletonList(
                    new HostnamePortRequirement(host, portValue)),
                SSHAuthenticator.matcher(ClientSession.class))) {
          if (StringUtils.equals(value, o.value)) {
            return FormValidation.ok();
          }
        }
      } catch (NumberFormatException e) {
        return FormValidation.warning(e, Messages.SSHLauncher_PortNotANumber());
      }
      return FormValidation.error(Messages.SSHLauncher_SelectedCredentialsMissing());
    }

    @RequirePOST
    public FormValidation doCheckPort(@QueryParameter String value) {
      if (StringUtils.isEmpty(value)) {
        return FormValidation.error(Messages.SSHLauncher_PortNotSpecified());
      }
      try {
        int portValue = Integer.parseInt(value);
        if (portValue <= 0) {
          return FormValidation.error(Messages.SSHLauncher_PortLessThanZero());
        }
        if (portValue >= 65536) {
          return FormValidation.error(Messages.SSHLauncher_PortMoreThan65535());
        }
        return FormValidation.ok();
      } catch (NumberFormatException e) {
        return FormValidation.error(e, Messages.SSHLauncher_PortNotANumber());
      }
    }

    @RequirePOST
    public FormValidation doCheckHost(@QueryParameter String value) {
      FormValidation ret = FormValidation.ok();
      if (StringUtils.isEmpty(value)) {
        return FormValidation.error(Messages.SSHLauncher_HostNotSpecified());
      }
      return ret;
    }

    @RequirePOST
    public FormValidation doCheckJavaPath(@QueryParameter String value) {
      FormValidation ret = FormValidation.ok();
      if (value != null && value.contains(" ") && !(value.startsWith("\"") && value.endsWith("\""))
          && !(value.startsWith("'") && value.endsWith("'"))) {
        return FormValidation.warning(Messages.SSHLauncher_JavaPathHasWhiteSpaces());
      }
      return ret;
    }

    // TODO add a connection verifier
  }

  // TODO refactor and extract.
  private class ServerHostKeyVerifierImpl implements ServerHostKeyVerifier {

    private final SlaveComputer computer;
    private final TaskListener listener;

    public ServerHostKeyVerifierImpl(final SlaveComputer computer, final TaskListener listener) {
      this.computer = computer;
      this.listener = listener;
    }

    public boolean verifyServerHostKey(String hostname, int port, String serverHostKeyAlgorithm, byte[] serverHostKey)
        throws Exception {

      final HostKey key = new HostKey(serverHostKeyAlgorithm, serverHostKey);

      return getSshHostKeyVerificationStrategyDefaulted().verify(computer, key, listener);
    }
  }
}
