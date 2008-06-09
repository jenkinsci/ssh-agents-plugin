package hudson.plugins.sshslaves;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.SFTPException;
import com.trilead.ssh2.SFTPv3Client;
import com.trilead.ssh2.SFTPv3FileAttributes;
import com.trilead.ssh2.SFTPv3FileHandle;
import com.trilead.ssh2.Session;
import com.trilead.ssh2.StreamGobbler;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Messages;
import hudson.remoting.Channel;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import hudson.util.IOException2;
import hudson.util.StreamCopyThread;
import hudson.util.StreamTaskListener;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A computer launcher that tries to start a linux slave by opening an SSH connection and trying to find java.
 */
public class SSHLauncher extends ComputerLauncher {

    /**
     * Field host
     */
    private final String host;

    /**
     * Field port
     */
    private final int port;

    /**
     * Field username
     */
    private final String username;

    /**
     * Field password
     *
     * @todo remove password once authentication is stored in the descriptor.
     */
    private final String password;

    /**
     * Field connection
     */
    private transient Connection connection;
    private static final int BUFFER_SIZE = 2048;

    /**
     * Constructor SSHLauncher creates a new SSHLauncher instance.
     *
     * @param host     The host to connect to.
     * @param port     The port to connect on.
     * @param username The username to connect as.
     * @param password The password to connect with.
     */
    @DataBoundConstructor
    public SSHLauncher(String host, int port, String username, String password) {
        this.host = host;
        this.port = port == 0 ? 22 : port;
        this.username = username;
        this.password = password;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isLaunchSupported() {
        return true;
    }

    /**
     * Gets the formatted current time stamp.
     *
     * @return the formatted current time stamp.
     */
    private static String getTimestamp() {
        return String.format("[%1$tD %1$tT]", new Date());
    }

    /**
     * Returns the remote root workspace (without trailing slash).
     *
     * @param computer The slave computer to get the root workspace of.
     *
     * @return the remote root workspace (without trailing slash).
     */
    private static String getWorkingDirectory(SlaveComputer computer) {
        String workingDirectory = computer.getNode().getRemoteFS();
        while (workingDirectory.endsWith("/")) {
            workingDirectory = workingDirectory.substring(0, workingDirectory.length() - 1);
        }
        return workingDirectory;
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void launch(final SlaveComputer computer, final StreamTaskListener listener) {
        connection = new Connection(host, port);
        try {
            openConnection(listener);

            String java = findJava(listener);

            String workingDirectory = getWorkingDirectory(computer);

            copySlaveJar(listener, workingDirectory);

            startSlave(computer, listener, java, workingDirectory);

        } catch (IOException e) {
            e.printStackTrace(listener.getLogger());
            connection.close();
            connection = null;
            listener.getLogger().println(getTimestamp() + " [SSH] Connection closed.");
        }
    }

    /**
     * Starts the slave process.
     *
     * @param computer         The computer.
     * @param listener         The listener.
     * @param java             The full path name of the java executable to use.
     * @param workingDirectory The working directory from which to start the java process.
     *
     * @throws IOException If something goes wrong.
     */
    private void startSlave(SlaveComputer computer, final StreamTaskListener listener, String java,
                            String workingDirectory) throws IOException {
        final Session session = connection.openSession();
        try {
            // TODO handle escaping fancy characters in paths
            session.execCommand("cd " + workingDirectory + " && " + java + " -jar slave.jar");
            final StreamGobbler out = new StreamGobbler(session.getStdout());
            final StreamGobbler err = new StreamGobbler(session.getStderr());

            // capture error information from stderr. this will terminate itself
            // when the process is killed.
            new StreamCopyThread("stderr copier for remote agent on " + computer.getDisplayName(),
                    err, listener.getLogger()).start();

            computer.setChannel(out, session.getStdin(), listener.getLogger(), new Channel.Listener() {
                public void onClosed(Channel channel, IOException cause) {
                    if (cause != null) {
                        cause.printStackTrace(listener.error(Messages.Slave_Terminated(getTimestamp())));
                    }
                    try {
                        session.close();
                    } catch (Throwable t) {
                        t.printStackTrace(listener.error("closed"));
                    }
                    try {
                        out.close();
                    } catch (Throwable t) {
                        t.printStackTrace(listener.error("closed"));
                    }
                    try {
                        err.close();
                    } catch (Throwable t) {
                        t.printStackTrace(listener.error("closed"));
                    }
                }
            });

        } catch (InterruptedException e) {
            session.close();
            e.printStackTrace(listener.error("aborted"));
        }
    }

    /**
     * Method copies the slave jar to the remote system.
     *
     * @param listener         The listener.
     * @param workingDirectory The directory into whihc the slave jar will be copied.
     *
     * @throws IOException If something goes wrong.
     */
    private void copySlaveJar(StreamTaskListener listener, String workingDirectory) throws IOException {
        String fileName = workingDirectory + "/slave.jar";

        listener.getLogger().println(getTimestamp() + " [SSH] Starting sftp client...");
        SFTPv3Client sftpClient = null;
        try {
            sftpClient = new SFTPv3Client(connection);

            try {
                // TODO decide best permissions and handle errors if exists already
                SFTPv3FileAttributes fileAttributes;
                try {
                    fileAttributes = sftpClient.stat(workingDirectory);
                } catch (SFTPException e) {
                    fileAttributes = null;
                }
                if (fileAttributes == null) {
                    listener.getLogger().println(getTimestamp() + " [SSH] Remote file system root '" + workingDirectory
                            + "' does not exist. Will try to create it");
                    // TODO mkdir -p mode
                    sftpClient.mkdir(workingDirectory, 0700);
                } else if (fileAttributes.isRegularFile()) {
                    throw new IOException("Remote file system root '" + workingDirectory
                            + "' is a file not a directory or a symlink");
                }

                // The file will be overwritten even if it already exists
                listener.getLogger().println(getTimestamp() + " [SSH] Copying latest slave.jar...");
                SFTPv3FileHandle fileHandle = sftpClient.createFile(fileName);

                InputStream is = null;
                try {
                    is = Hudson.getInstance().servletContext.getResourceAsStream("/WEB-INF/slave.jar");
                    byte[] buf = new byte[BUFFER_SIZE];

                    listener.getLogger().println(getTimestamp() + " [SSH] Sending data...");

                    int count = 0;
                    int len;
                    try {
                        while ((len = is.read(buf)) != -1) {
                            sftpClient.write(fileHandle, (long) count, buf, 0, len);
                            count += len;
                        }
                        listener.getLogger().println(getTimestamp() + " [SSH] Sent " + count + " bytes.");
                    } catch (Exception e) {
                        listener.getLogger().println(getTimestamp() + " [SSH] Error writing to remote file");
                        throw new IOException2("Error writing to remote file", e);
                    }
                } finally {
                    if (is != null) {
                        is.close();
                    }
                }
            } catch (Exception e) {
                listener.getLogger().println(getTimestamp() + " [SSH] Error creating file");
                e.printStackTrace(listener.getLogger());
                throw new IOException2("Could not copy slave.jar to slave", e);
            }
        } finally {
            if (sftpClient != null) {
                sftpClient.close();
            }
        }
    }

    private String findJava(StreamTaskListener listener) throws IOException {
        String java;
        java = "java";
        listener.getLogger().println(getTimestamp() + " [SSH] Checking default java version");
        String line = null;
        Session session = connection.openSession();
        try {
            session.execCommand(java + " -version");
            StreamGobbler out = new StreamGobbler(session.getStdout());
            StreamGobbler err = new StreamGobbler(session.getStderr());
            try {
                BufferedReader r1 = new BufferedReader(new InputStreamReader(out));
                BufferedReader r2 = new BufferedReader(new InputStreamReader(err));

                // TODO make sure this works with IBM JVM & JRocket

                outer:
                for (BufferedReader r : new BufferedReader[]{r1, r2}) {
                    while (null != (line = r.readLine())) {
                        if (line.startsWith("java version \"")) {
                            break outer;
                        }
                    }
                }
            } finally {
                out.close();
                err.close();
            }
        } finally {
            session.close();
        }

        if (line == null || !line.startsWith("java version \"")) {
            throw new IOException("The default version of java is either unsupported version or unknown");
        }

        line = line.substring(line.indexOf('\"') + 1, line.lastIndexOf('\"'));
        listener.getLogger().println(getTimestamp() + " [SSH] " + java + " version = " + line);

        // TODO make this version check a bit less hacky
        if (line.compareTo("1.5") < 0) {
            // TODO find a java that is at least 1.5
            throw new IOException("Could not find a version of java that is at least version 1.5");
        }
        return java;
    }

    private void openConnection(StreamTaskListener listener) throws IOException {
        listener.getLogger().println(getTimestamp() + " [SSH] Opening SSH connection to " + host + ":" + port);
        connection.connect();

        // TODO if using a key file, use the key file instead of password
        listener.getLogger().println(getTimestamp() + " [SSH] Authenticating as " + username + "/******");
        boolean isAuthenicated = connection.authenticateWithPassword(username, password);

        if (isAuthenicated && connection.isAuthenticationComplete()) {
            listener.getLogger().println(getTimestamp() + " [SSH] Authentication successful.");
        } else {
            listener.getLogger().println(getTimestamp() + " [SSH] Authentication failed.");
            connection.close();
            connection = null;
            listener.getLogger().println(getTimestamp() + " [SSH] Connection closed.");
            throw new IOException("Authentication failed.");
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void afterDisconnect(SlaveComputer slaveComputer, StreamTaskListener listener) {
        String workingDirectory = getWorkingDirectory(slaveComputer);
        String fileName = workingDirectory + "/slave.jar";

        if (connection != null) {

            SFTPv3Client sftpClient = null;
            try {
                sftpClient = new SFTPv3Client(connection);
                sftpClient.rm(fileName);
            } catch (Exception e) {
                listener.getLogger().println(getTimestamp() + " [SSH] Error deleting file");
                e.printStackTrace(listener.getLogger());
            } finally {
                if (sftpClient != null) {
                    sftpClient.close();
                }
            }

            connection.close();
            connection = null;
            listener.getLogger().println(getTimestamp() + " [SSH] Connection closed.");
        }
        super.afterDisconnect(slaveComputer, listener);
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

    /**
     * Getter for property 'username'.
     *
     * @return Value for property 'username'.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Getter for property 'password'.
     *
     * @return Value for property 'password'.
     */
    public String getPassword() {
        return password;
    }

    /**
     * {@inheritDoc}
     */
    public Descriptor<ComputerLauncher> getDescriptor() {
        return DESCRIPTOR;
    }

    /**
     * Field DESCRIPTOR
     */
    public static final Descriptor<ComputerLauncher> DESCRIPTOR = new DescriptorImpl();

    private static class DescriptorImpl extends Descriptor<ComputerLauncher> {

        // TODO move the authentication storage to descriptor... see SubversionSCM.java

        // TODO add support for key files

        /**
         * Constructs a new DescriptorImpl.
         */
        protected DescriptorImpl() {
            super(SSHLauncher.class);
        }

        /**
         * {@inheritDoc}
         */
        public String getDisplayName() {
            return "Launch slave agents on Linux machines via SSH";
        }

    }
}
