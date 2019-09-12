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

import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.domains.HostnamePortRequirement;
import com.cloudbees.plugins.credentials.domains.SchemeRequirement;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.google.common.io.ByteStreams;
import com.trilead.ssh2.ChannelCondition;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.SCPClient;
import com.trilead.ssh2.SFTPv3Client;
import com.trilead.ssh2.SFTPv3FileAttributes;
import com.trilead.ssh2.ServerHostKeyVerifier;
import com.trilead.ssh2.Session;
import com.trilead.ssh2.jenkins.SFTPClient;
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
import hudson.util.NamingThreadFactory;
import hudson.util.NullStream;
import java.util.Collections;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.InterruptedException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static hudson.Util.fixEmpty;

/**
 * A computer launcher that tries to start a linux agent by opening an SSH connection and trying to find java.
 */
public class SSHLauncher extends ComputerLauncher {

    /**
     * The scheme requirement.
     */
    public static final SchemeRequirement SSH_SCHEME = new SchemeRequirement("ssh");
    public static final Integer DEFAULT_MAX_NUM_RETRIES = 10;
    public static final Integer DEFAULT_RETRY_WAIT_TIME = 15;
    public static final Integer DEFAULT_LAUNCH_TIMEOUT_SECONDS = DEFAULT_MAX_NUM_RETRIES * DEFAULT_RETRY_WAIT_TIME + 60;
    public static final String AGENT_JAR = "remoting.jar";
    public static final String SLASH_AGENT_JAR = "/" + AGENT_JAR;
    public static final String WORK_DIR_PARAM = " -workDir ";
    public static final String JAR_CACHE_PARAM = " -jar-cache ";
    public static final String JAR_CACHE_DIR = "/remoting/jarCache";
    public static final int DEFAULT_SSH_PORT = 22;

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
    private String credentialsId;

    /**
     * Transient stash of the credentials to use, mostly just for providing floating user object.
     */
    private transient StandardUsernameCredentials credentials;

    /**
     * Field jvmOptions.
     */
    private String jvmOptions;

    /**
     * Field javaPath.
     */
    public String javaPath;

    /**
     * SSH connection to the agent.
     */
    private transient volatile Connection connection;

    /**
     * Indicates that the {@link #tearDownConnection(SlaveComputer, TaskListener)} is in progress.
     * It is used in {@link #afterDisconnect(SlaveComputer, TaskListener)} to avoid multiple parallel calls.
     */
    private transient volatile boolean tearingDownConnection;

    /**
     * The session inside {@link #connection} that controls the agent process.
     */
    private transient Session session;

    /**
     * Field prefixStartSlaveCmd.
     */
    public String prefixStartSlaveCmd;

    /**
     *  Field suffixStartSlaveCmd.
     */
    public String suffixStartSlaveCmd;

    /**
     *  Field launchTimeoutSeconds.
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

    // TODO: It is a bad idea to create a new Executor service for each launcher.
    // Maybe a Remoting thread pool should be used, but it requires the logic rework to Futures
    /**
     * Keeps executor service for the async launch operation.
     */
    @CheckForNull
    private transient volatile ExecutorService launcherExecutorService;

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
     * @see <a href="https://github.com/jenkinsci/remoting/blob/master/docs/workDir.md#remoting-work-directory">Remoting Work directory</a>
     */
    private String workDir;


    private class ServerHostKeyVerifierImpl implements ServerHostKeyVerifier {

        private final SlaveComputer computer;
        private final TaskListener listener;

        public ServerHostKeyVerifierImpl(final SlaveComputer computer, final TaskListener listener) {
            this.computer = computer;
            this.listener = listener;
        }

        @Override
        public boolean verifyServerHostKey(String hostname, int port, String serverHostKeyAlgorithm, byte[] serverHostKey) throws Exception {

            final HostKey key = new HostKey(serverHostKeyAlgorithm, serverHostKey);

            return getSshHostKeyVerificationStrategyDefaulted().verify(computer, key, listener);
        }
    }

    /**
     * Constructor SSHLauncher creates a new SSHLauncher instance.
     *
     * @param host       The host to connect to.
     * @param port       The port to connect on.
     * @param credentialsId The credentials id to connect as.
     * @param jvmOptions Options passed to the java vm.
     * @param javaPath   Path to the host jdk installation. If <code>null</code> the jdk will be auto detected.
     * @param prefixStartSlaveCmd This will prefix the start agent command. For instance if you want to execute the command with a different shell.
     * @param suffixStartSlaveCmd This will suffix the start agent command.
     * @param launchTimeoutSeconds Launch timeout in seconds
     * @param maxNumRetries The number of times to retry connection if the SSH connection is refused during initial connect
     * @param retryWaitTime The number of seconds to wait between retries
     * @param sshHostKeyVerificationStrategy Host key verification method selected.
     */
    public SSHLauncher(@NonNull String host, int port, String credentialsId,
             String jvmOptions, String javaPath, String prefixStartSlaveCmd, String suffixStartSlaveCmd,
             Integer launchTimeoutSeconds, Integer maxNumRetries, Integer retryWaitTime, SshHostKeyVerificationStrategy sshHostKeyVerificationStrategy) {
        setHost(host);
        setJvmOptions(jvmOptions);
        setPort(port);
        this.credentialsId = credentialsId;
        setJavaPath(javaPath);
        setPrefixStartSlaveCmd(prefixStartSlaveCmd);
        setSuffixStartSlaveCmd(suffixStartSlaveCmd);
        setLaunchTimeoutSeconds(launchTimeoutSeconds);
        setMaxNumRetries(maxNumRetries);
        setRetryWaitTime(retryWaitTime);
        this.sshHostKeyVerificationStrategy = sshHostKeyVerificationStrategy;
    }

    /**
     * Constructor SSHLauncher creates a new SSHLauncher instance.
     *
     * @param host       The host to connect to.
     * @param port       The port to connect on.
     * @param credentialsId The credentials id to connect as.
     */
    @DataBoundConstructor
    public SSHLauncher(@NonNull String host, int port, String credentialsId) {
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
                        .lookupCredentials(StandardUsernameCredentials.class, Jenkins.get(), ACL.SYSTEM,
                                SSH_SCHEME),
                CredentialsMatchers.withId(credentialsId)
        );
    }

    public Object readResolve(){
        if(tcpNoDelay == null){
            tcpNoDelay = true;
        }

        if(this.launchTimeoutSeconds == null || launchTimeoutSeconds <= 0){
            this.launchTimeoutSeconds = DEFAULT_LAUNCH_TIMEOUT_SECONDS;
        }

        if(this.maxNumRetries == null){
            this.maxNumRetries = DEFAULT_MAX_NUM_RETRIES;
        }

        if(this.retryWaitTime == null){
            this.retryWaitTime = DEFAULT_RETRY_WAIT_TIME;
        }
        return this;
    }

    public StandardUsernameCredentials getCredentials() {
        String credentialsId = this.credentialsId == null
                ? (this.credentials == null ? null : this.credentials.getId())
                : this.credentialsId;
        try {
            // only ever want from the system
            // lookup every time so that we always have the latest
            StandardUsernameCredentials credentials = credentialsId != null ? 
                    SSHLauncher.lookupSystemCredentials(credentialsId) : null;
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
        return true;
    }

    /**
     * Gets the optional JVM Options used to launch the agent JVM.
     * @return The optional JVM Options used to launch the agent JVM.
     */
    public String getJvmOptions() {
        return jvmOptions == null ? "" : jvmOptions;
    }

    /**
     * Gets the optional java command to use to launch the agent JVM.
     * @return The optional java command to use to launch the agent JVM.
     */
    @SuppressWarnings("unused") // Used by vsphere-cloud-plugin
    @Deprecated
    public String getJavaPath() {
        return javaPath == null ? "" : javaPath;
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
     * @param computer The slave computer to get the root workspace of.
     *
     * @return the remote root workspace (without trailing slash).
     *
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
            if(connection != null){
                listener.getLogger().println(Messages.SSHLauncher_alreadyConnected());
                return;
            }
            connection = new Connection(host, port);
            launcherExecutorService = Executors.newSingleThreadExecutor(
                    new NamingThreadFactory(Executors.defaultThreadFactory(), "SSHLauncher.launch for '" + computer.getName() + "' node"));
            Set<Callable<Boolean>> callables = new HashSet<>();
            callables.add(new Callable<Boolean>() {
                public Boolean call() throws InterruptedException {
                    Boolean rval = Boolean.FALSE;
                    try {
                        String[] preferredKeyAlgorithms = getSshHostKeyVerificationStrategyDefaulted().getPreferredKeyAlgorithms(computer);
                        if (preferredKeyAlgorithms != null && preferredKeyAlgorithms.length > 0) { // JENKINS-44832
                            connection.setServerHostKeyAlgorithms(preferredKeyAlgorithms);
                        } else {
                            listener.getLogger().println("Warning: no key algorithms provided; JENKINS-42959 disabled");
                        }

                        listener.getLogger().println(logConfiguration());

                        openConnection(listener, computer);

                        verifyNoHeaderJunk(listener);
                        reportEnvironment(listener);

                        final String workingDirectory = getWorkingDirectory(computer);
                        if (workingDirectory == null) {
                            listener.error("Cannot get the working directory for " + computer);
                            return Boolean.FALSE;
                        }

                        String java = null;
                        if (StringUtils.isNotBlank(javaPath)) {
                            java = expandExpression(computer, javaPath);
                        } else {
                            JavaVersionChecker javaVersionChecker = new JavaVersionChecker(computer, listener, getJvmOptions(),
                                    connection);
                            java = javaVersionChecker.resolveJava();
                        }

                        copyAgentJar(listener, workingDirectory);

                        startAgent(computer, listener, java, workingDirectory);

                        PluginImpl.register(connection);
                        rval = Boolean.TRUE;
                    } catch (RuntimeException e) {
                        e.printStackTrace(listener.error(Messages.SSHLauncher_UnexpectedError()));
                    } catch (Error e) {
                        e.printStackTrace(listener.error(Messages.SSHLauncher_UnexpectedError()));
                    } catch (AbortException e) {
                        listener.getLogger().println(e.getMessage());
                    } catch (IOException e) {
                        e.printStackTrace(listener.getLogger());
                    } finally {
                        return rval;
                    }
                }
            });

            final String nodeName = node != null ? node.getNodeName(): "unknown";
            try {
                long time = System.currentTimeMillis();
                List<Future<Boolean>> results;
                final ExecutorService srv = launcherExecutorService;
                if (srv == null) {
                    throw new IllegalStateException("Launcher Executor Service should be always non-null here, because the task allocates and closes service on its own");
                }

                results = srv.invokeAll(callables);
                long duration = System.currentTimeMillis() - time;
                Boolean res;
                try {
                    res = results.get(0).get();
                } catch (CancellationException | ExecutionException e) {
                    res = Boolean.FALSE;
                    listener.getLogger().println(Messages.SSHLauncher_launchCanceled());
                }
                if (!res) {
                    System.out.println(Messages.SSHLauncher_LaunchFailedDuration(getTimestamp(),
                            nodeName, host, duration));
                    listener.getLogger().println(getTimestamp() + " Launch failed - cleaning up connection");
                    cleanupConnection(listener);
                } else {
                    System.out.println(Messages.SSHLauncher_LaunchCompletedDuration(getTimestamp(),
                            nodeName, host, duration));
                }
            } catch (InterruptedException e) {
                System.out.println(Messages.SSHLauncher_LaunchFailed(getTimestamp(),
                        nodeName, host));
            } finally {
                ExecutorService srv = launcherExecutorService;
                if (srv != null) {
                    srv.shutdownNow();
                    launcherExecutorService = null;
                }
            }
        }
        if (node != null && getTrackCredentials()) {
            CredentialsProvider.track(node, getCredentials());
        }
    }

    /**
     * Called to terminate the SSH connection. Used liberally when we back out from an error.
     */
    private void cleanupConnection(TaskListener listener) {
        // we might be called multiple times from multiple finally/catch block, 
        if (connection!=null) {
            connection.close();
            connection = null;
            listener.getLogger().println(Messages.SSHLauncher_ConnectionClosed(getTimestamp()));
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
     * Makes sure that SSH connection won't produce any unwanted text, which will interfere with sftp execution.
     */
    private void verifyNoHeaderJunk(TaskListener listener) throws IOException, InterruptedException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        connection.exec("exit 0",baos);
        final String s;
        //TODO: Seems we need to retrieve the encoding from the connection destination
        try {
            s = baos.toString(Charset.defaultCharset().name());
        } catch (UnsupportedEncodingException ex) { // Should not happen
            throw new IOException("Default encoding is unsupported", ex);
        }
        
        if (s.length()!=0) {
            listener.getLogger().println(Messages.SSHLauncher_SSHHeaderJunkDetected());
            listener.getLogger().println(s);
            throw new AbortException();
        }
    }

    /**
     * Starts the agent process.
     *
     * @param computer         The computer.
     * @param listener         The listener.
     * @param java             The full path name of the java executable to use.
     * @param workingDirectory The working directory from which to start the java process.
     *
     * @throws IOException If something goes wrong.
     */
    private void startAgent(SlaveComputer computer, final TaskListener listener, String java,
                            String workingDirectory) throws IOException {
        session = connection.openSession();
        expandChannelBufferSize(session,listener);
        String cmd = "cd \"" + workingDirectory + "\" && " + java + " " + getJvmOptions() + " -jar " + AGENT_JAR +
                     getWorkDirParam(workingDirectory);

        //This will wrap the cmd with prefix commands and suffix commands if they are set.
        cmd = getPrefixStartSlaveCmd() + cmd + getSuffixStartSlaveCmd();

        listener.getLogger().println(Messages.SSHLauncher_StartingAgentProcess(getTimestamp(), cmd));
        session.execCommand(cmd);

        session.pipeStderr(new DelegateNoCloseOutputStream(listener.getLogger()));

        try {
            computer.setChannel(session.getStdout(), session.getStdin(), listener.getLogger(), null);
        } catch (InterruptedException e) {
            session.close();
            throw new IOException(Messages.SSHLauncher_AbortedDuringConnectionOpen(), e);
        } catch (IOException e) {
            try {
                // often times error this early means the JVM has died, so let's see if we can capture all stderr
                // and exit code
                throw new AbortException(getSessionOutcomeMessage(session,false));
            } catch (InterruptedException x) {
                throw new IOException(e);
            }
        }
    }

    private void expandChannelBufferSize(Session session, TaskListener listener) {
            // see hudson.remoting.Channel.PIPE_WINDOW_SIZE for the discussion of why 1MB is in the right ball park
            // but this particular session is where all the master/agent communication will happen, so
            // it's worth using a bigger buffer to really better utilize bandwidth even when the latency is even larger
            // (and since we are draining this pipe very rapidly, it's unlikely that we'll actually accumulate this much data)
            int sz = 4;
            session.setWindowSize(sz*1024*1024);
            listener.getLogger().println("Expanded the channel window size to "+sz+"MB");
    }

    /**
     * Method copies the agent jar to the remote system.
     *
     * @param listener         The listener.
     * @param workingDirectory The directory into which the agent jar will be copied.
     *
     * @throws IOException If something goes wrong.
     */
    private void copyAgentJar(TaskListener listener, String workingDirectory) throws IOException, InterruptedException {
        String fileName = workingDirectory + SLASH_AGENT_JAR;

        listener.getLogger().println(Messages.SSHLauncher_StartingSFTPClient(getTimestamp()));
        SFTPClient sftpClient = null;
        try {
            sftpClient = new SFTPClient(connection);

            try {
                SFTPv3FileAttributes fileAttributes = sftpClient._stat(workingDirectory);
                if (fileAttributes==null) {
                    listener.getLogger().println(Messages.SSHLauncher_RemoteFSDoesNotExist(getTimestamp(),
                            workingDirectory));
                    sftpClient.mkdirs(workingDirectory, 0700);
                } else if (fileAttributes.isRegularFile()) {
                    throw new IOException(Messages.SSHLauncher_RemoteFSIsAFile(workingDirectory));
                }

                listener.getLogger().println(Messages.SSHLauncher_CopyingAgentJar(getTimestamp()));
                byte[] agentJar = new Slave.JnlpJar(AGENT_JAR).readFully();

                // If the agent jar already exists see if it needs to be updated
                boolean overwrite = true;
                if (sftpClient.exists(fileName)) {
                    String sourceAgentHash = getMd5Hash(agentJar);
                    String existingAgentHash = getMd5Hash(readInputStreamIntoByteArrayAndClose(sftpClient.read(fileName)));
                    listener.getLogger().println(MessageFormat.format( "Source agent hash is {0}. "
                      + "Installed agent hash is {1}", sourceAgentHash, existingAgentHash));

                    overwrite = !sourceAgentHash.equals(existingAgentHash);
                }

                if (overwrite) {
                    try {
                        // try to delete the file in case the agent we are copying is shorter than the agent
                        // that is already there
                        sftpClient.rm(fileName);
                    } catch (IOException e) {
                        // the file did not exist... so no need to delete it!
                    }

                    try (OutputStream os = sftpClient.writeToFile(fileName)) {
                        os.write(agentJar);
                        listener.getLogger()
                          .println(Messages.SSHLauncher_CopiedXXXBytes(getTimestamp(), agentJar.length));
                    } catch (Error error) {
                        throw error;
                    } catch (Throwable e) {
                        throw new IOException(Messages.SSHLauncher_ErrorCopyingAgentJarTo(fileName), e);
                    }
                }else{
                    listener.getLogger().println("Verified agent jar. No update is necessary.");
                }
            } catch (Error error) {
                throw error;
            } catch (Throwable e) {
                throw new IOException(Messages.SSHLauncher_ErrorCopyingAgentJarInto(workingDirectory), e);
            }
        } catch (IOException e) {
            if (sftpClient == null) {
                e.printStackTrace(listener.error(Messages.SSHLauncher_StartingSCPClient(getTimestamp())));
                // lets try to recover if the agent doesn't have an SFTP service
                copySlaveJarUsingSCP(listener, workingDirectory);
            } else {
                throw e;
            }
        } finally {
            if (sftpClient != null) {
                sftpClient.close();
            }
        }
    }

    /**
     * Method reads a byte array and returns an upper case md5 hash for it.
     *
     * @param bytes
     * @return
     * @throws NoSuchAlgorithmException
     */
    static String getMd5Hash(byte[] bytes) throws NoSuchAlgorithmException {

        String hash;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(bytes);
            byte[] digest = md.digest();

            char[] hexCode = "0123456789ABCDEF".toCharArray();
            StringBuilder r = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                r.append(hexCode[(b >> 4) & 0xF]);
                r.append(hexCode[(b & 0xF)]);
            }

            hash = r.toString().toUpperCase();
        }catch (NoSuchAlgorithmException e){
            throw e;
        }
        return hash;
    }

    /**
     * Method reads an input stream into a byte array and closes the input stream when finished.
     * Added for reading the remoting jar and generating a hash value for it.
     *
     * @param inputStream
     * @return
     * @throws IOException
     */
    static byte[] readInputStreamIntoByteArrayAndClose(InputStream inputStream) throws IOException {

        byte[] bytes = null;
        try{
            bytes = ByteStreams.toByteArray(inputStream);
        }catch(IOException e){
            throw e;
        } finally {
            IOUtils.closeQuietly(inputStream);
            if(bytes==null) {
                bytes = new byte[ 1 ];
            }
        }

        return bytes;
    }

    /**
     * Method copies the agent jar to the remote system using scp.
     *
     * @param listener         The listener.
     * @param workingDirectory The directory into which the agent jar will be copied.
     *
     * @throws IOException If something goes wrong.
     * @throws InterruptedException If something goes wrong.
     */
    private void copySlaveJarUsingSCP(TaskListener listener, String workingDirectory) throws IOException, InterruptedException {
        SCPClient scp = new SCPClient(connection);
        try {
            // check if the working directory exists
            if (connection.exec("test -d " + workingDirectory ,listener.getLogger())!=0) {
                listener.getLogger().println(
                        Messages.SSHLauncher_RemoteFSDoesNotExist(getTimestamp(), workingDirectory));
                // working directory doesn't exist, lets make it.
                if (connection.exec("mkdir -p " + workingDirectory, listener.getLogger())!=0) {
                    listener.getLogger().println("Failed to create "+workingDirectory);
                }
            }

            // delete the agent jar as we do with SFTP
            connection.exec("rm " + workingDirectory + SLASH_AGENT_JAR, new NullStream());

            // SCP it to the agent. hudson.Util.ByteArrayOutputStream2 doesn't work for this. It pads the byte array.
            listener.getLogger().println(Messages.SSHLauncher_CopyingAgentJar(getTimestamp()));
            scp.put(new Slave.JnlpJar(AGENT_JAR).readFully(), AGENT_JAR, workingDirectory, "0644");
        } catch (IOException e) {
            throw new IOException(Messages.SSHLauncher_ErrorCopyingAgentJarInto(workingDirectory), e);
        }
    }

    protected void reportEnvironment(TaskListener listener) throws IOException, InterruptedException {
        listener.getLogger().println(Messages._SSHLauncher_RemoteUserEnvironment(getTimestamp()));
        connection.exec("set",listener.getLogger());
    }

    protected void openConnection(final TaskListener listener, final SlaveComputer computer) throws IOException, InterruptedException {
        PrintStream logger = listener.getLogger();
        logger.println(Messages.SSHLauncher_OpeningSSHConnection(getTimestamp(), host + ":" + port));
        connection.setTCPNoDelay(getTcpNoDelay());

        int maxNumRetries = getMaxNumRetries();
        for (int i = 0; i <= maxNumRetries; i++) {
            try {
                int launchTimeoutMillis = (int)getLaunchTimeoutMillis();
                connection.connect(new ServerHostKeyVerifierImpl(computer, listener),
                        launchTimeoutMillis, 0 /*read timeout - JENKINS-48618*/,
                        (int) (launchTimeoutMillis + TimeUnit.SECONDS.toMillis(5)));
                break;
            } catch (Exception ex) {
                String message = "unknown error";
                Throwable cause = ex.getCause();
                if (cause != null) {
                    message = cause.getMessage();
                    logger.println(message);
                } else if(ex.getMessage() != null){
                    message = ex.getMessage();
                    logger.println(message);
                }

                connection.close();

                if (maxNumRetries - i > 0) {
                    logger.println("SSH Connection failed with IOException: \"" + message
                            + "\", retrying in " + getRetryWaitTime() + " seconds." +
                            " There are " + (maxNumRetries - i) + " more retries left.");
                }
            }
            Thread.sleep(TimeUnit.SECONDS.toMillis(getRetryWaitTime()));
        }

        StandardUsernameCredentials credentials = getCredentials();
        if (credentials == null) {
            throw new AbortException("Cannot find SSH User credentials with id: " + credentialsId);
        }
        if (SSHAuthenticator.newInstance(connection, credentials).authenticate(listener)
                && connection.isAuthenticationComplete()) {
            logger.println(Messages.SSHLauncher_AuthenticationSuccessful(getTimestamp()));
        } else {
            logger.println(Messages.SSHLauncher_AuthenticationFailed(getTimestamp()));
            throw new AbortException(Messages.SSHLauncher_AuthenticationFailedException());
        }
    }

    private void checkConfig() throws InterruptedException {
        //JENKINS-58340 some plugins does not implement Descriptor
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
        FormValidation validateCredentials = descriptor.doCheckCredentialsId(Jenkins.get(), Jenkins.get(), this.host, port, this.credentialsId);

        if(validatePort.kind == FormValidation.Kind.ERROR){
            isValid = false;
            message += validatePort.getMessage() + "\n";
        }
        if(validateHost.kind == FormValidation.Kind.ERROR){
            isValid = false;
            message += validateHost.getMessage() + "\n";
        }
        if(validateCredentials.kind == FormValidation.Kind.ERROR){
            isValid = false;
            message += validateCredentials.getMessage() + "\n";
        }

        if(!isValid){
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
        }

        ExecutorService srv = launcherExecutorService;
        if (srv != null) {
            // If the service is still running, shut it down and interrupt the operations if any
            srv.shutdown();
        }

        if (tearingDownConnection) {
            // tear down operation is in progress, do not even try to synchronize the call.
            //TODO: what if reconnect attempts collide? It should not be possible due to locks, but maybe it worth investigation
            LOGGER.log(Level.FINE, "There is already a tear down operation in progress for connection {0}. Skipping the call", connection);
            return;
        }
        tearDownConnection(slaveComputer, listener);
    }

    private synchronized void tearDownConnection(@NonNull SlaveComputer slaveComputer, final @NonNull TaskListener listener) {
        if (connection != null) {
            tearDownConnectionImpl(slaveComputer, listener);
        }
    }

    private void tearDownConnectionImpl(@NonNull SlaveComputer slaveComputer, final @NonNull TaskListener listener) {
        try {
            tearingDownConnection = true;
            boolean connectionLost = reportTransportLoss(connection, listener);
            if (session!=null) {
                // give the process 3 seconds to write out its dying message before we cut the loss
                // and give up on this process. if the agent process had JVM crash, OOME, or any other
                // critical problem, this will allow us to capture that.
                // exit code is also an useful info to figure out why the process has died.
                try {
                    listener.getLogger().println(getSessionOutcomeMessage(session,connectionLost));
                    session.getStdout().close();
                    session.close();
                } catch (Throwable t) {
                    t.printStackTrace(listener.error(Messages.SSHLauncher_ErrorWhileClosingConnection()));
                }
                session = null;
            }

            PluginImpl.unregister(connection);
            cleanupConnection(listener);
        } finally {
            tearingDownConnection = false;
        }
    }

    /**
     * If the SSH connection as a whole is lost, report that information.
     */
    private boolean reportTransportLoss(Connection c, TaskListener listener) {
        Throwable cause = c.getReasonClosedCause();
        if (cause != null) {
            cause.printStackTrace(listener.error("Socket connection to SSH server was lost"));
        }

        return cause != null;
    }

    /**
     * Find the exit code or exit status, which are differentiated in SSH protocol.
     */
    private String getSessionOutcomeMessage(Session session, boolean isConnectionLost) throws InterruptedException {
        session.waitForCondition(ChannelCondition.EXIT_STATUS | ChannelCondition.EXIT_SIGNAL, 3000);

        Integer exitCode = session.getExitStatus();
        if (exitCode != null)
            return "Slave JVM has terminated. Exit code=" + exitCode;

        String sig = session.getExitSignal();
        if (sig != null)
            return "Slave JVM has terminated. Exit signal=" + sig;

        if (isConnectionLost)
            return "Slave JVM has not reported exit code before the socket was lost";

        return "Slave JVM has not reported exit code. Is it still running?";
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @CheckForNull
    public SshHostKeyVerificationStrategy getSshHostKeyVerificationStrategy() {
        return sshHostKeyVerificationStrategy;
    }

    @NonNull
    SshHostKeyVerificationStrategy getSshHostKeyVerificationStrategyDefaulted() {
        return sshHostKeyVerificationStrategy != null ? sshHostKeyVerificationStrategy : new NonVerifyingKeyVerificationStrategy();
    }

    @DataBoundSetter
    public void setSshHostKeyVerificationStrategy(SshHostKeyVerificationStrategy value) {
        this.sshHostKeyVerificationStrategy = value;
    }

    @DataBoundSetter
    public void setJvmOptions(String value){
        this.jvmOptions = fixEmpty(value);
    }

    @DataBoundSetter
    public void setJavaPath(String value){
        this.javaPath = fixEmpty(value);
    }

    @DataBoundSetter
    public void setPrefixStartSlaveCmd(String value){
        this.prefixStartSlaveCmd = fixEmpty(value);
    }

    @DataBoundSetter
    public void setSuffixStartSlaveCmd(String value){
        this.suffixStartSlaveCmd = fixEmpty(value);
    }

    @DataBoundSetter
    public void setMaxNumRetries(Integer value){
        this.maxNumRetries = value != null && value >= 0 ? value : DEFAULT_MAX_NUM_RETRIES;
    }

    @DataBoundSetter
    public void setLaunchTimeoutSeconds(Integer value){
        this.launchTimeoutSeconds = value == null || value <= 0 ? DEFAULT_LAUNCH_TIMEOUT_SECONDS : value;
    }

    @DataBoundSetter
    public void setRetryWaitTime(Integer value){
        this.retryWaitTime = value != null && value >= 0 ? value : DEFAULT_RETRY_WAIT_TIME;
    }

    public void setPort(int value){
        this.port = value == 0 ? DEFAULT_SSH_PORT : value;
    }

    public void setHost(String value){
        this.host = Util.fixEmptyAndTrim(value);
    }

    /**
     * Getter for property 'host'.
     *
     * @return Value for property 'host'.
     */
    public String getHost() {
        return host;
    }

    /**
     * Getter for property 'port'.
     *
     * @return Value for property 'port'.
     */
    public int getPort() {
        return port;
    }



    public Connection getConnection() {
        return connection;
    }

    @NonNull
    public String getPrefixStartSlaveCmd() {
        return Util.fixNull(prefixStartSlaveCmd);
    }

    @NonNull
    public String getSuffixStartSlaveCmd() {
        return Util.fixNull(suffixStartSlaveCmd);
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

    private long getLaunchTimeoutMillis() {
        return launchTimeoutSeconds == null || launchTimeoutSeconds < 0 ? DEFAULT_LAUNCH_TIMEOUT_SECONDS : TimeUnit.SECONDS.toMillis
                (launchTimeoutSeconds);
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
     * Getter for property 'retryWaitTime'
     *
     * @return retryWaitTime
     */
    @NonNull
    public Integer getRetryWaitTime() {
        return retryWaitTime == null || retryWaitTime < 0 ? DEFAULT_RETRY_WAIT_TIME : retryWaitTime;
    }

    public boolean getTcpNoDelay() {
        return tcpNoDelay != null ? tcpNoDelay : true;
    }

    @DataBoundSetter
    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    /**
     * Enable/Disable the credential tracking, this tracking store information about where it is used a credential,
     * in this case in a node. If the tracking is enabled and you launch a big number of Agents per day, activate
     * credentials tacking could cause a performance issue see
     * @see  <a href="https://issues.jenkins-ci.org/browse/JENKINS-49235">JENKINS-49235</a>
     */
    public boolean getTrackCredentials() {
        String trackCredentials = System.getProperty(SSHLauncher.class.getName() + ".trackCredentials");
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
     * @param workingDirectory The Working directory set on the configuration of the node.
     * @return the remoting parameter to set the workDir,
     * by default it is the same as the working directory configured on the node so "-workDir " + workingDirectory,
     * if workDir is set, he method will return "-workDir " + getWorkDir()
     * if the parameter is set in suffixStartSlaveCmd, the method will return an empty String.
     */
    @NonNull
    @Restricted(NoExternalUse.class)
    public String getWorkDirParam(@NonNull String workingDirectory){
        String ret;
        if(getSuffixStartSlaveCmd().contains(WORK_DIR_PARAM) || getSuffixStartSlaveCmd().contains(JAR_CACHE_PARAM)){
            //the parameter is already set on suffixStartSlaveCmd
            ret = "";
        } else if (StringUtils.isNotBlank(getWorkDir())){
            ret = WORK_DIR_PARAM + getWorkDir() + JAR_CACHE_PARAM + getWorkDir() + JAR_CACHE_DIR;
        } else {
            ret = WORK_DIR_PARAM + workingDirectory + JAR_CACHE_PARAM + workingDirectory + JAR_CACHE_DIR;
        }
        return ret;
    }

    @Extension
    @Symbol({"ssh", "sSHLauncher"})
    public static class DescriptorImpl extends Descriptor<ComputerLauncher> {

        // TODO move the authentication storage to descriptor... see SubversionSCM.java

        /**
         * {@inheritDoc}
         */
        public String getDisplayName() {
            return Messages.SSHLauncher_DescriptorDisplayName();
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
            if (n==null)
                n = Jenkins.get().getDescriptorOrDie(SSHConnector.class).getHelpFile(fieldName);
            return n;
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath AccessControlled context,
                                                     @QueryParameter String host,
                                                     @QueryParameter String port,
                                                     @QueryParameter String credentialsId) {
            Jenkins jenkins = Jenkins.get();
            if ((context == jenkins && !jenkins.hasPermission(Computer.CREATE)) || (context != jenkins && !context.hasPermission(Computer.CONFIGURE))) {
                return new StandardUsernameListBoxModel()
                        .includeCurrentValue(credentialsId);
            }
            try {
                int portValue = Integer.parseInt(port);
                return new StandardUsernameListBoxModel()
                        .includeMatchingAs(
                                ACL.SYSTEM,
                                jenkins,
                                StandardUsernameCredentials.class,
                                Collections.singletonList(
                                        new HostnamePortRequirement(host, portValue)
                                ),
                                SSHAuthenticator.matcher(Connection.class))
                        .includeCurrentValue(credentialsId); // always add the current value last in case already present
            } catch (NumberFormatException ex) {
                return new StandardUsernameListBoxModel()
                        .includeCurrentValue(credentialsId);
            }
        }

        public FormValidation doCheckCredentialsId(@AncestorInPath ItemGroup context,
                                                   @AncestorInPath AccessControlled _context,
                                                   @QueryParameter String host,
                                                   @QueryParameter String port,
                                                   @QueryParameter String value) {
            Jenkins jenkins = Jenkins.get();
            if ((_context == jenkins && !jenkins.hasPermission(Computer.CREATE)) || (_context != jenkins && !_context.hasPermission(Computer.CONFIGURE))) {
                return FormValidation.ok(); // no need to alarm a user that cannot configure
            }
            try {
                int portValue = Integer.parseInt(port);
                for (ListBoxModel.Option o : CredentialsProvider
                        .listCredentials(StandardUsernameCredentials.class, context, ACL.SYSTEM,
                                Collections.singletonList(
                                        new HostnamePortRequirement(host, portValue)
                                ),
                                SSHAuthenticator.matcher(Connection.class))) {
                    if (StringUtils.equals(value, o.value)) {
                        return FormValidation.ok();
                    }
                }
            } catch (NumberFormatException e) {
                return FormValidation.warning(e, Messages.SSHLauncher_PortNotANumber());
            }
            return FormValidation.error(Messages.SSHLauncher_SelectedCredentialsMissing());
        }

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

        public FormValidation doCheckHost(@QueryParameter String value) {
            FormValidation ret = FormValidation.ok();
            if (StringUtils.isEmpty(value)) {
                return FormValidation.error(Messages.SSHLauncher_HostNotSpecified());
            }
            return ret;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(SSHLauncher.class.getName());

    private static class DelegateNoCloseOutputStream extends OutputStream {
        private OutputStream out;

        public DelegateNoCloseOutputStream(OutputStream out) {
            this.out = out;
        }

        @Override
        public void write(int b) throws IOException {
            if (out != null) out.write(b);
        }

        @Override
        public void close() throws IOException {
            out = null;
        }

        @Override
        public void flush() throws IOException {
            if (out != null) out.flush();
        }

        @Override
        public void write(byte[] b) throws IOException {
            if (out != null) out.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (out != null) out.write(b, off, len);
        }
    }

//    static {
//        com.trilead.ssh2.log.Logger.enabled = true;
//        com.trilead.ssh2.log.Logger.logger = new DebugLogger() {
//            public void log(int level, String className, String message) {
//                System.out.println(className+"\n"+message);
//            }
//        };
//    }

    public String logConfiguration() {
        final StringBuilder sb = new StringBuilder("SSHLauncher{");
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
        sb.append(", sshHostKeyVerificationStrategy=").append(sshHostKeyVerificationStrategy != null ?
                                                              sshHostKeyVerificationStrategy.getClass().getName() : "None");
        sb.append(", tcpNoDelay=").append(getTcpNoDelay());
        sb.append(", trackCredentials=").append(getTrackCredentials());
        sb.append('}');
        return sb.toString();
    }
}
