/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: MIT
 */
package io.jenkins.plugins.sshbuildagents.ssh.mina;

import static hudson.Util.fixEmpty;

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
import jenkins.model.Jenkins;
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

/**
 * A computer launcher that tries to start a linux agent by opening an SSH connection and trying to
 * find java.
 *
 */
public class SSHApacheMinaLauncher extends ComputerLauncher {
    /** The scheme requirement. */
    public static final SchemeRequirement SSH_SCHEME = new SchemeRequirement("ssh");

    /** Default maximum number of retries for SSH connections. */
    public static final Integer DEFAULT_MAX_NUM_RETRIES = 10;

    /** Default wait time between retries in seconds. */
    public static final Integer DEFAULT_RETRY_WAIT_TIME = 15;

    /** Default launch timeout in seconds. */
    public static final Integer DEFAULT_LAUNCH_TIMEOUT_SECONDS = 60;

    /** Default remoting jar file name. */
    public static final String AGENT_JAR = "remoting.jar";

    /** Default remoting jar file name with leading slash. */
    public static final String SLASH_AGENT_JAR = "/" + AGENT_JAR;

    /** Working directory parameter for remoting. */
    public static final String WORK_DIR_PARAM = " -workDir ";

    /** JAR cache parameter for remoting. */
    public static final String JAR_CACHE_PARAM = " -jar-cache ";

    /** JAR cache directory for remoting. */
    public static final String JAR_CACHE_DIR = "/remoting/jarCache";

    /** Default SSH port. */
    public static final int DEFAULT_SSH_PORT = 22;

    private static final Logger LOGGER = Logger.getLogger(SSHApacheMinaLauncher.class.getName());

    /** Field javaPath. */
    private String javaPath;

    /** Field prefixStartAgentCmd. */
    private String prefixStartAgentCmd;

    /** Field suffixStartAgentCmd. */
    private String suffixStartAgentCmd;

    /** Field launchTimeoutSeconds. */
    private Integer launchTimeoutSeconds;

    /** Field maxNumRetries. */
    private Integer maxNumRetries;

    /** Field retryWaitTime (seconds). */
    private Integer retryWaitTime;

    /** Field host */
    private String host;

    /** Field port */
    private int port;

    /** The id of the credentials to use. */
    private final String credentialsId;

    /** Transient stash of the credentials to use, mostly just for providing floating user object. */
    private transient StandardUsernameCredentials credentials;

    /** Field jvmOptions. */
    private String jvmOptions;

    /** SSH connection to the agent. */
    private transient volatile Connection connection;

    /**
     * The verifier to use for checking the SSH key presented by the host responding to the connection
     */
    @CheckForNull
    private SshHostKeyVerificationStrategy sshHostKeyVerificationStrategy;

    /** Allow to enable/disable the TCP_NODELAY flag on the SSH connection. */
    private Boolean tcpNoDelay;

    /**
     * Set the value to add to the remoting parameter -workDir
     *
     * @see <a href=
     *     "https://github.com/jenkinsci/remoting/blob/master/docs/workDir.md#remoting-work-directory">Remoting
     *     Work directory</a>
     */
    private String workDir;

    /** Shell channel to execute the remoting process. */
    @CheckForNull
    private transient ShellChannel shellChannel;

    /**
     * Constructor SSHLauncher creates a new SSHLauncher instance.
     *
     * @param host The host to connect to.
     * @param port The port to connect on.
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

    /**
     * Looks up the system credentials by id.
     *
     * @param credentialsId The credentials id to look up.
     * @return The credentials or null if not found.
     */
    public static StandardUsernameCredentials lookupSystemCredentials(String credentialsId) {
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentialsInItemGroup(
                        StandardUsernameCredentials.class, Jenkins.get(), ACL.SYSTEM2, List.of(SSH_SCHEME)),
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

    /**
     * Returns the remote root workspace (without trailing slash).
     *
     * @param agent The agent to get the root workspace of.
     * @return the remote root workspace (without trailing slash).
     */
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

    /**
     * Gets the credentials used to connect to the agent.
     *
     * @return The credentials used to connect to the agent.
     */
    public StandardUsernameCredentials getCredentials() {
        String credentialsId = this.credentialsId == null
                ? (this.credentials == null ? null : this.credentials.getId())
                : this.credentialsId;
        try {
            // only ever want from the system
            // lookup every time so that we always have the latest
            StandardUsernameCredentials credentials =
                    credentialsId != null ? SSHApacheMinaLauncher.lookupSystemCredentials(credentialsId) : null;
            if (credentials != null) {
                this.credentials = credentials;
                return credentials;
            }
        } catch (Throwable t) {
            // ignore
        }

        return this.credentials;
    }

    /** {@inheritDoc} */
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

    /** Sets the optional JVM Options used to launch the agent JVM. */
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

    /** {@inheritDoc} */
    @Override
    public void launch(@NonNull final SlaveComputer computer, @NonNull final TaskListener listener)
            throws InterruptedException {
        final Node node = computer.getNode();
        final String host = this.host;
        final int port = this.port;
        if (computer == null || listener == null) {
            throw new IllegalArgumentException(Messages.SSHLauncher_ComputerAndListenerMustNotBeNull());
        }
        checkConfig();

        synchronized (this) {
            if (connection != null) {
                listener.getLogger().println(Messages.SSHLauncher_alreadyConnected());
                return;
            }
            connection = new ConnectionImpl(host, port);

            final String workingDirectory = getWorkingDirectory(computer);
            if (workingDirectory == null || workingDirectory.isEmpty()) {
                listener.getLogger().println(Messages.SSHLauncher_WorkingDirectoryNotSet());
                throw new IllegalArgumentException(Messages.SSHLauncher_WorkingDirectoryNotSet());
            }
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

    /**
     * Expands the given expression using the environment variables.
     *
     * @param computer The computer to get the environment variables from.
     * @param expression The expression to expand.
     * @return The expanded expression.
     */
    private String expandExpression(SlaveComputer computer, String expression) {
        return getEnvVars(computer).expand(expression);
    }

    /**
     * Gets the environment variables for the given computer.
     *
     * @param computer The computer to get the environment variables from.
     * @return The environment variables for the computer.
     */
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

    /**
     * Gets the environment variables for the given Jenkins instance.
     *
     * @param h The Jenkins instance to get the environment variables from.
     */
    private EnvVars getEnvVars(Jenkins h) {
        return getEnvVars(h.getGlobalNodeProperties());
    }

    /**
     * Gets the environment variables for the given node.
     *
     * @param n The node to get the environment variables from.
     * @return The environment variables for the node.
     */
    private EnvVars getEnvVars(Node n) {
        return getEnvVars(n.getNodeProperties());
    }

    /**
     * Gets the environment variables for the given list of node properties.
     *
     * @param dl The list of node properties to get the environment variables from.
     * @return The environment variables for the node properties.
     */
    private EnvVars getEnvVars(DescribableList<NodeProperty<?>, NodePropertyDescriptor> dl) {
        final EnvironmentVariablesNodeProperty evnp = dl.get(EnvironmentVariablesNodeProperty.class);
        if (evnp == null) {
            return null;
        }

        return evnp.getEnvVars();
    }

    /**
     * Makes sure that SSH connection won't produce any unwanted text, which will interfere with sftp
     * execution. TODO review if it is needed or move to the SSH Provider.
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
     * @param computer The computer.
     * @param listener The listener.
     * @param workingDirectory The working directory from which to start the java process.
     * @throws IOException If something goes wrong.
     */
    private void startAgent(SlaveComputer computer, final TaskListener listener, String workingDirectory)
            throws IOException {
        String java = "java";
        if (StringUtils.isNotBlank(javaPath)) {
            java = expandExpression(computer, javaPath);
        }

        String cmd = "cd \""
                + workingDirectory
                + "\" && "
                + java
                + " "
                + getJvmOptions()
                + " -jar "
                + AGENT_JAR
                + getWorkDirParam(workingDirectory);

        // This will wrap the cmd with prefix commands and suffix commands if they are
        // set.
        cmd = getPrefixStartAgentCmd() + cmd + getSuffixStartAgentCmd();

        listener.getLogger().println(Messages.SSHLauncher_StartingAgentProcess(getTimestamp(), cmd));
        shellChannel = connection.shellChannel();
        shellChannel.execCommand(cmd);
        try {
            computer.setChannel(
                    shellChannel.getInvertedStdout(), shellChannel.getInvertedStdin(), listener.getLogger(), null);
        } catch (InterruptedException e) {
            throw new IOException(Messages.SSHLauncher_AbortedDuringConnectionOpen(), e);
        } catch (IOException e) {
            throw new AbortException(e.getMessage());
        }
    }

    /**
     * Method copies the agent jar to the remote system.
     *
     * @param listener The listener.
     * @param workingDirectory The directory into which the agent jar will be copied.
     * @throws IOException If something goes wrong.
     */
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

    /**
     * Reports the environment variables for the remote user on the Agent logs.
     *
     * @param listener The listener to report the environment variables to.
     * @throws IOException If an error occurs while reporting the environment variables.
     */
    protected void reportEnvironment(TaskListener listener) throws IOException {
        listener.getLogger().println(Messages._SSHLauncher_RemoteUserEnvironment(getTimestamp()));
        connection.execCommand("set");
    }

    /**
     * Opens a connection to the remote host.
     *
     * @param listener The listener to report progress to.
     * @param computer The computer to connect to.
     * @param workingDirectory The working directory on the remote host.
     * @throws IOException If an error occurs while opening the connection.
     */
    protected void openConnection(
            final TaskListener listener, final SlaveComputer computer, final String workingDirectory)
            throws IOException {
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
        String[] preferredKeyAlgorithms =
                getSshHostKeyVerificationStrategyDefaulted().getPreferredKeyAlgorithms(computer);
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

    /**
     * Validates the Agent configuration.
     *
     * @throws InterruptedException
     */
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
        FormValidation validateCredentials =
                descriptor.doCheckCredentialsId(Jenkins.get(), Jenkins.get(), this.host, port, this.credentialsId);

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

    /** {@inheritDoc} */
    @Override
    public void afterDisconnect(SlaveComputer slaveComputer, final TaskListener listener) {
        if (connection == null) {
            // Nothing to do here, the connection is not established
            return;
        } else {
            try {
                if (shellChannel != null) {
                    if (shellChannel.getLastError() != null) {
                        listener.getLogger()
                                .println("\tException: "
                                        + shellChannel.getLastError().getMessage());
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

    /** Closes the SSH connection and any associated channels. */
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
            LOGGER.fine("Error closing connection: " + e.getMessage());
        }
        connection = null;
        shellChannel = null;
    }

    /**
     * Gets the credentials id used to connect to the agent.
     *
     * @return
     */
    public String getCredentialsId() {
        return credentialsId;
    }

    /** Getter for property 'sshHostKeyVerificationStrategy'. */
    @CheckForNull
    public SshHostKeyVerificationStrategy getSshHostKeyVerificationStrategy() {
        return sshHostKeyVerificationStrategy;
    }

    /**
     * Setter for property 'sshHostKeyVerificationStrategy'.
     *
     * @param value The SSH host key verification strategy to set.
     */
    @DataBoundSetter
    public void setSshHostKeyVerificationStrategy(SshHostKeyVerificationStrategy value) {
        this.sshHostKeyVerificationStrategy = value;
    }

    /**
     * Gets the SSH host key verification strategy, defaulting to a non-verifying strategy if none is
     * set.
     *
     * @return The SSH host key verification strategy.
     */
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

    /**
     * Sets the port to connect to.
     *
     * @param value The port to connect to.
     */
    public void setPort(int value) {
        this.port = value == 0 ? DEFAULT_SSH_PORT : value;
    }

    /**
     * Gets the SSH connection.
     *
     * @return The SSH connection.
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * Gets the prefix command to run before starting the agent.
     *
     * @return The prefix command to run before starting the agent.
     */
    @NonNull
    public String getPrefixStartAgentCmd() {
        return Util.fixNull(prefixStartAgentCmd);
    }

    /**
     * Sets the prefix command to run before starting the agent.
     *
     * @param value The prefix command to run before starting the agent.
     */
    @DataBoundSetter
    public void setPrefixStartAgentCmd(String value) {
        this.prefixStartAgentCmd = fixEmpty(value);
    }

    /**
     * Gets the suffix command to run after starting the agent.
     *
     * @return The suffix command to run after starting the agent.
     */
    @NonNull
    public String getSuffixStartAgentCmd() {
        return Util.fixNull(suffixStartAgentCmd);
    }

    /**
     * Sets the suffix command to run after starting the agent.
     *
     * @param value The suffix command to run after starting the agent.
     */
    @DataBoundSetter
    public void setSuffixStartAgentCmd(String value) {
        this.suffixStartAgentCmd = fixEmpty(value);
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

    /**
     * Sets the launch timeout in seconds.
     *
     * @param value The launch timeout in seconds.
     */
    @DataBoundSetter
    public void setLaunchTimeoutSeconds(Integer value) {
        this.launchTimeoutSeconds = value == null || value <= 0 ? DEFAULT_LAUNCH_TIMEOUT_SECONDS : value;
    }

    /**
     * Gets the launch timeout in milliseconds.
     *
     * @return The launch timeout in milliseconds.
     */
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

    /**
     * Sets the maximum number of retries.
     *
     * @param value The maximum number of retries.
     */
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

    /**
     * Sets the time to wait between retries in seconds.
     *
     * @param value The time to wait between retries in seconds.
     */
    @DataBoundSetter
    public void setRetryWaitTime(Integer value) {
        this.retryWaitTime = value != null && value >= 0 ? value : DEFAULT_RETRY_WAIT_TIME;
    }

    /**
     * Gets the TCP_NODELAY flag for the SSH connection.
     *
     * @return true if TCP_NODELAY is enabled, false otherwise.
     */
    public boolean getTcpNoDelay() {
        return tcpNoDelay != null ? tcpNoDelay : true;
    }

    /**
     * Sets the TCP_NODELAY flag for the SSH connection.
     *
     * @param tcpNoDelay true to enable TCP_NODELAY, false to disable it.
     */
    @DataBoundSetter
    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    /**
     * Enable/Disable the credential tracking, this tracking store information about where it is used
     * a credential, in this case in a node. If the tracking is enabled and you launch a big number of
     * Agents per day, activate credentials tacking could cause a performance issue see
     *
     * @see <a href= "https://issues.jenkins-ci.org/browse/JENKINS-49235">JENKINS-49235</a>
     */
    public boolean getTrackCredentials() {
        String trackCredentials = System.getProperty(SSHApacheMinaLauncher.class.getName() + ".trackCredentials");
        return !"false".equalsIgnoreCase(trackCredentials);
    }

    /**
     * Sets the working directory for the remoting process.
     *
     * @return The working directory for the remoting process.
     */
    public String getWorkDir() {
        return workDir;
    }

    /**
     * Sets the working directory for the remoting process.
     *
     * @param workDir The working directory for the remoting process.
     */
    @DataBoundSetter
    public void setWorkDir(String workDir) {
        this.workDir = Util.fixEmptyAndTrim(workDir);
    }

    /**
     * @param workingDirectory The Working directory set on the configuration of the node.
     * @return the remoting parameter to set the workDir, by default it is the same as the working
     *     directory configured on the node so "-workDir " + workingDirectory, if workDir is set, he
     *     method will return "-workDir " + getWorkDir() if the parameter is set in
     *     suffixStartAgentCmd, the method will return an empty String.
     */
    @NonNull
    @Restricted(NoExternalUse.class)
    public String getWorkDirParam(@NonNull String workingDirectory) {
        String ret;
        if (getSuffixStartAgentCmd().contains(WORK_DIR_PARAM)
                || getSuffixStartAgentCmd().contains(JAR_CACHE_PARAM)) {
            // the parameter is already set on suffixStartAgentCmd
            ret = "";
        } else if (StringUtils.isNotBlank(getWorkDir())) {
            ret = WORK_DIR_PARAM + getWorkDir() + JAR_CACHE_PARAM + getWorkDir() + JAR_CACHE_DIR;
        } else {
            ret = WORK_DIR_PARAM + workingDirectory + JAR_CACHE_PARAM + workingDirectory + JAR_CACHE_DIR;
        }
        return ret;
    }

    /**
     * Returns a string representation of the configuration for logging purposes.
     *
     * @return A string representation of the configuration.
     */
    public String logConfiguration() {
        final StringBuilder sb = new StringBuilder(this.getClass().getName() + "{");
        sb.append("host='").append(getHost()).append('\'');
        sb.append(", port=").append(getPort());
        sb.append(", credentialsId='").append(Util.fixNull(credentialsId)).append('\'');
        sb.append(", jvmOptions='").append(getJvmOptions()).append('\'');
        sb.append(", javaPath='").append(Util.fixNull(javaPath)).append('\'');
        sb.append(", prefixStartAgentCmd='").append(getPrefixStartAgentCmd()).append('\'');
        sb.append(", suffixStartAgentCmd='").append(getSuffixStartAgentCmd()).append('\'');
        sb.append(", launchTimeoutSeconds=").append(getLaunchTimeoutSeconds());
        sb.append(", maxNumRetries=").append(getMaxNumRetries());
        sb.append(", retryWaitTime=").append(getRetryWaitTime());
        sb.append(", sshHostKeyVerificationStrategy=")
                .append(
                        sshHostKeyVerificationStrategy != null
                                ? sshHostKeyVerificationStrategy.getClass().getName()
                                : "None");
        sb.append(", tcpNoDelay=").append(getTcpNoDelay());
        sb.append(", trackCredentials=").append(getTrackCredentials());
        sb.append('}');
        return sb.toString();
    }

    @Extension
    @Symbol({"ssh", "sshMinaLauncher"})
    public static class DescriptorImpl extends Descriptor<ComputerLauncher> {

        /** {@inheritDoc} */
        public String getDisplayName() {
            return Messages.SSHApacheMinaLauncher_DescriptorDisplayName();
        }

        public Class getSshConnectorClass() {
            return SSHConnector.class;
        }

        /** Delegates the help link to the {@link SSHConnector}. */
        @Override
        public String getHelpFile(String fieldName) {
            String n = super.getHelpFile(fieldName);
            if (n == null)
                n = Jenkins.get().getDescriptorOrDie(SSHConnector.class).getHelpFile(fieldName);
            return n;
        }

        /**
         * Return the list of credentials ids that can be used to connect to the remote host.
         *
         * @param context The context in which the credentials are being requested.
         * @param host The host to connect to.
         * @param port The port to connect on.
         * @param credentialsId The current credentials id, if any.
         * @return A list of credentials ids that can be used to connect to the remote host.
         */
        @RequirePOST
        public ListBoxModel doFillCredentialsIdItems(
                @AncestorInPath AccessControlled context,
                @QueryParameter String host,
                @QueryParameter String port,
                @QueryParameter String credentialsId) {
            Jenkins jenkins = Jenkins.get();
            if ((context == jenkins && !jenkins.hasPermission(Computer.CREATE))
                    || (context != jenkins && !context.hasPermission(Computer.CONFIGURE))) {
                return new StandardUsernameListBoxModel().includeCurrentValue(credentialsId);
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
                                Collections.singletonList(new HostnamePortRequirement(host, portValue)),
                                SSHAuthenticator.matcher(ClientSession.class))
                        .includeCurrentValue(
                                credentialsId); // always add the current value last in case already present
            } catch (NumberFormatException ex) {
                return new StandardUsernameListBoxModel().includeCurrentValue(credentialsId);
            }
        }

        /**
         * Checks if the given credentials id is valid for the given host and port.
         *
         * @param context The context in which the credentials are being checked.
         * @param host The host to connect to.
         * @param port The port to connect on.
         * @param value The credentials id to check.
         * @return A FormValidation indicating whether the credentials id is valid or not.
         */
        @RequirePOST
        public FormValidation doCheckCredentialsId(
                @AncestorInPath ItemGroup context,
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
                for (ListBoxModel.Option o : CredentialsProvider.listCredentialsInItemGroup(
                        StandardUsernameCredentials.class,
                        context,
                        ACL.SYSTEM2,
                        // Collections.singletonList(SSH_SCHEME),
                        Collections.singletonList(new HostnamePortRequirement(host, portValue)),
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

        /**
         * Checks if the given port is valid.
         *
         * @param value The port to check.
         * @return A FormValidation indicating whether the port is valid or not.
         */
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

        /**
         * Checks if the given host is valid.
         *
         * @param value The host to check.
         * @return A FormValidation indicating whether the host is valid or not.
         */
        @RequirePOST
        public FormValidation doCheckHost(@QueryParameter String value) {
            FormValidation ret = FormValidation.ok();
            if (StringUtils.isEmpty(value)) {
                return FormValidation.error(Messages.SSHLauncher_HostNotSpecified());
            }
            return ret;
        }

        /**
         * Checks if the given java path is valid.
         *
         * @param value The java path to check.
         * @return A FormValidation indicating whether the java path is valid or not.
         */
        @RequirePOST
        public FormValidation doCheckJavaPath(@QueryParameter String value) {
            FormValidation ret = FormValidation.ok();
            if (value != null
                    && value.contains(" ")
                    && !(value.startsWith("\"") && value.endsWith("\""))
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

        public boolean verifyServerHostKey(
                String hostname, int port, String serverHostKeyAlgorithm, byte[] serverHostKey) throws Exception {

            final HostKey key = new HostKey(serverHostKeyAlgorithm, serverHostKey);

            return getSshHostKeyVerificationStrategyDefaulted().verify(computer, key, listener);
        }
    }
}
