package hudson.plugins.sshslaves;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.trilead.ssh2.ChannelCondition;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.SCPClient;
import com.trilead.ssh2.SFTPv3FileAttributes;
import com.trilead.ssh2.ServerHostKeyVerifier;
import com.trilead.ssh2.Session;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import hudson.AbortException;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.plugins.sshslaves.verifiers.HostKey;
import hudson.plugins.sshslaves.verifiers.SshHostKeyVerificationStrategy;
import hudson.slaves.SlaveComputer;
import hudson.util.NullStream;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import static hudson.plugins.sshslaves.SSHLauncherConfig.getTimestamp;

public class SSHProviderImpl implements SSHProvider{

    public static final String REPORT_ENV = "set";
    public static final String TRUE_CMD = "exit 0";

    class ServerHostKeyVerifierImpl implements ServerHostKeyVerifier {
        @Override
        public boolean verifyServerHostKey(String hostname, int port, String serverHostKeyAlgorithm, byte[] serverHostKey) throws Exception {
            final HostKey key = new HostKey(serverHostKeyAlgorithm, serverHostKey);
            return getSshHostKeyVerificationStrategyDefaulted().verify(computer, key, listener);
        }
    }
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


    // Some of the messages observed in the wild:
    // "Connection refused (Connection refused)"
    // "Connection reset"
    // "Connection timed out", "Connection timed out (Connection timed out)"
    // "No route to host", "No route to host (Host unreachable)"
    // "Premature connection close"
    private static final List<String> RECOVERABLE_FAILURES = Arrays.asList(
            "Connection refused", "Connection reset", "Connection timed out", "No route to host", "Premature connection close"
    );

    /**
     * SSH connection to the agent.
     */
    private transient volatile Connection connection;

    /**
     * The session inside {@link #connection} that controls the agent process.
     */
    private transient Session session;

    private final SSHLauncherConfig config;
    private final SlaveComputer computer;
    private final TaskListener listener;

    public SSHProviderImpl(SSHLauncherConfig config, SlaveComputer computer, TaskListener listener) {
        this.config = config;
        this.computer = computer;
        this.listener = listener;
    }

    @Override
    public void close(){
        if(connection != null){
            connection.close();
        }
    }

    @Override
    public void execCommand(String cmd) throws IOException {
            session.execCommand(cmd);
    }

    /**
     * Makes sure that SSH connection won't produce any unwanted text, which will interfere with sftp execution.
     */
    private void verifyNoHeaderJunk() throws IOException, InterruptedException {
        BufferedReader r = exec(TRUE_CMD);
        String line = r.readLine();
        if(line != null){
            listener.getLogger().println(Messages.SSHLauncher_SSHHeaderJunkDetected());
            listener.getLogger().println(line);
            while (null != (line = r.readLine())) {
                listener.getLogger().println(line);
            }
            throw new AbortException("SSH connection produce unwanted text");
        }
    }

    @Override
    @NonNull
    public BufferedReader exec(String cmd) throws IOException, InterruptedException {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            connection.exec(cmd, baos);
            String s = "";
            //TODO: Seems we need to retrieve the encoding from the connection destination
            try {
                s = baos.toString(Charset.defaultCharset().name());
            } catch (UnsupportedEncodingException ex) { // Should not happen
                throw new IOException("Default encoding is unsupported", ex);
            }

            if (s.length() != 0) {
                listener.getLogger().println(s);
            }
            return new BufferedReader(new StringReader(s));
        } catch (IOException | InterruptedException e){
            listener.error("Failed to exec : " + cmd + " - " + e.getClass().getName()+ " - " + e.getMessage());
            throw new IOException(e);
        }
    }


    @Override
    public void openConnection() throws IOException, InterruptedException {
        PrintStream logger = listener.getLogger();
        logger.println(Messages.SSHLauncher_OpeningSSHConnection(getTimestamp(), getHost() + ":" + getPort()));
        connection = new Connection(getHost(), getPort());
        connection.setTCPNoDelay(getTcpNoDelay());

        int maxNumRetries = getMaxNumRetries();
        for (int i = 0; i <= maxNumRetries; i++) {
            try {
                // We pass launch timeout so that the connection will be able to abort once it reaches the timeout
                // It is a poor man's logic, but it should cause termination if the connection goes strongly beyond the timeout
                //TODO: JENKINS-48617 and JENKINS-48618 need to be implemented to make it fully robust
                int launchTimeoutMillis = (int)getLaunchTimeoutMillis();
                connection.connect(new ServerHostKeyVerifierImpl(), launchTimeoutMillis,
                                   0 /*read timeout - JENKINS-48618*/, launchTimeoutMillis);
                break;
            } catch (IOException ioexception) {
                @CheckForNull String message = "";
                Throwable cause = ioexception.getCause();
                if (cause != null) {
                    message = cause.getMessage();
                    logger.println(message);
                }
                if (cause == null || !isRecoverable(message)) {
                    throw ioexception;
                }
                if (maxNumRetries - i > 0) {
                    logger.println("SSH Connection failed with IOException: \"" + message
                                   + "\", retrying in " + getRetryWaitTime() + " seconds.  There "
                                   + "are " + (maxNumRetries - i) + " more retries left.");
                } else {
                    logger.println("SSH Connection failed with IOException: \"" + message + "\".");
                    throw ioexception;
                }
            }
            Thread.sleep(TimeUnit.SECONDS.toMillis(getRetryWaitTime()));
        }

        StandardUsernameCredentials credentials = getCredentials();
        if (credentials == null) {
            throw new AbortException("Cannot find SSH User credentials with id: " + getCredentialsId());
        }
        if (SSHAuthenticator.newInstance(connection, credentials).authenticate(listener)
            && connection.isAuthenticationComplete()) {
            logger.println(Messages.SSHLauncher_AuthenticationSuccessful(getTimestamp()));
        } else {
            logger.println(Messages.SSHLauncher_AuthenticationFailed(getTimestamp()));
            throw new AbortException(Messages.SSHLauncher_AuthenticationFailedException());
        }

        verifyNoHeaderJunk();
        reportEnvironment();
    }


    private boolean isRecoverable(@CheckForNull String message) {
        if (message == null) {
            return false;
        }

        for (String s : RECOVERABLE_FAILURES) {
            if (message.startsWith(s)) return true;
        }
        return false;
    }

    protected void reportEnvironment() throws IOException, InterruptedException {
        listener.getLogger().println(Messages._SSHLauncher_RemoteUserEnvironment(getTimestamp()));
        exec(REPORT_ENV);
    }

    /**
     * Method copies the agent jar to the remote system.
     *
     * @param workingDirectory The directory into whihc the agent jar will be copied.
     *
     * @throws IOException If something goes wrong.
     */
    public void copyAgentJar(String workingDirectory) throws IOException, InterruptedException {
        String fileName = workingDirectory + SSHLauncher.SLASH_AGENT_JAR;

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

                try {
                    // try to delete the file in case the agent we are copying is shorter than the agent
                    // that is already there
                    sftpClient.rm(fileName);
                } catch (IOException e) {
                    // the file did not exist... so no need to delete it!
                }

                listener.getLogger().println(Messages.SSHLauncher_CopyingAgentJar(getTimestamp()));

                try {
                    byte[] agentJar = new Slave.JnlpJar(SSHLauncher.AGENT_JAR).readFully();
                    OutputStream os = sftpClient.writeToFile(fileName);
                    try {
                        os.write(agentJar);
                    } finally {
                        os.close();
                    }
                    listener.getLogger().println(Messages.SSHLauncher_CopiedXXXBytes(getTimestamp(), agentJar.length));
                } catch (Error error) {
                    throw error;
                } catch (Throwable e) {
                    throw new IOException(Messages.SSHLauncher_ErrorCopyingAgentJarTo(fileName), e);
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
                copySlaveJarUsingSCP(workingDirectory);
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
     * Method copies the agent jar to the remote system using scp.
     *
     * @param workingDirectory The directory into which the agent jar will be copied.
     *
     * @throws IOException If something goes wrong.
     * @throws InterruptedException If something goes wrong.
     */
    private void copySlaveJarUsingSCP(String workingDirectory) throws IOException, InterruptedException {
        SCPClient scp = new SCPClient(connection);
        try {
            // check if the working directory exists
            //TODO extract command
            if (connection.exec("test -d " + workingDirectory ,listener.getLogger())!=0) {
                listener.getLogger().println(
                        Messages.SSHLauncher_RemoteFSDoesNotExist(getTimestamp(), workingDirectory));
                // working directory doesn't exist, lets make it.
                //TODO extract command
                if (connection.exec("mkdir -p " + workingDirectory, listener.getLogger())!=0) {
                    listener.getLogger().println("Failed to create "+workingDirectory);
                }
            }

            // delete the agent jar as we do with SFTP
            //TODO extract command
            connection.exec("rm " + workingDirectory + SSHLauncher.SLASH_AGENT_JAR, new NullStream());

            // SCP it to the agent. hudson.Util.ByteArrayOutputStream2 doesn't work for this. It pads the byte array.
            listener.getLogger().println(Messages.SSHLauncher_CopyingAgentJar(getTimestamp()));
            scp.put(new Slave.JnlpJar(SSHLauncher.AGENT_JAR).readFully(), SSHLauncher.AGENT_JAR, workingDirectory, "0644");
        } catch (IOException e) {
            throw new IOException(Messages.SSHLauncher_ErrorCopyingAgentJarInto(workingDirectory), e);
        }
    }

    /**
     * Starts the agent process.
     *
     * @param java             The full path name of the java executable to use.
     * @param workingDirectory The working directory from which to start the java process.
     *
     * @throws IOException If something goes wrong.
     */
    public void startAgent(String java, String workingDirectory) throws IOException {
        session = connection.openSession();
        expandChannelBufferSize(session,listener);
        String cmd = "cd \"" + workingDirectory + "\" && " + java + " " + getJvmOptions() + " -jar " + SSHLauncher.AGENT_JAR +
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

    /**
     * Called to terminate the SSH connection. Used liberally when we back out from an error.
     */
    public void cleanupConnection() {
        // we might be called multiple times from multiple finally/catch block,
        if (connection!=null) {
            connection.close();
            connection = null;
            listener.getLogger().println(Messages.SSHLauncher_ConnectionClosed(getTimestamp()));
        }
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

    private void expandChannelBufferSize(Session session, TaskListener listener) {
        // see hudson.remoting.Channel.PIPE_WINDOW_SIZE for the discussion of why 1MB is in the right ball park
        // but this particular session is where all the master/agent communication will happen, so
        // it's worth using a bigger buffer to really better utilize bandwidth even when the latency is even larger
        // (and since we are draining this pipe very rapidly, it's unlikely that we'll actually accumulate this much data)
        int sz = 4;
        session.setWindowSize(sz*1024*1024);
        listener.getLogger().println("Expanded the channel window size to "+sz+"MB");
    }

    public String getWorkDirParam(@NonNull String workingDirectory){
        return config.getWorkDirParam(workingDirectory);
    }

    public String getCredentialsId() {
        return config.getCredentialsId();
    }

    @NonNull
    public SshHostKeyVerificationStrategy getSshHostKeyVerificationStrategyDefaulted() {
        return config.getSshHostKeyVerificationStrategyDefaulted();
    }

    @CheckForNull
    public SshHostKeyVerificationStrategy getSshHostKeyVerificationStrategy() {
        return config.getSshHostKeyVerificationStrategy();
    }

    public StandardUsernameCredentials getCredentials() {
        return config.getCredentials();
    }

    public String getJvmOptions() {
        return config.getJvmOptions();
    }

    public String getHost() {
        return config.getHost();
    }

    public int getPort() {
        return config.getPort();
    }

    @NonNull
    public String getPrefixStartSlaveCmd() {
        return config.getPrefixStartSlaveCmd();
    }

    @NonNull
    public String getSuffixStartSlaveCmd() {
        return config.getSuffixStartSlaveCmd();
    }

    @NonNull
    public Integer getLaunchTimeoutSeconds() {
        return config.getLaunchTimeoutSeconds();
    }

    public long getLaunchTimeoutMillis() {
        return config.getLaunchTimeoutMillis();
    }

    @NonNull
    public Integer getMaxNumRetries() {
        return config.getMaxNumRetries();
    }

    @NonNull
    public Integer getRetryWaitTime() {
        return config.getRetryWaitTime();
    }

    public boolean getTcpNoDelay() {
        return config.getTcpNoDelay();
    }

    public boolean getTrackCredentials() {
        return config.getTrackCredentials();
    }

    public String getWorkDir() {
        return config.getWorkDir();
    }

    public String getJavaPath() {
        return config.getJavaPath();
    }
}
