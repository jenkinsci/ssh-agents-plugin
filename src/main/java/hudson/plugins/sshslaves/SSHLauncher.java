package hudson.plugins.sshslaves;

import com.trilead.ssh2.*;
import hudson.model.Descriptor;
import hudson.model.Messages;
import hudson.remoting.Channel;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import hudson.util.StreamCopyThread;
import hudson.util.StreamTaskListener;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;

/**
 * A computer launcher that tries to start a linux slave by opening an SSH connection and trying to find java.
 */
public class SSHLauncher extends ComputerLauncher {

    private final String host;
    private final int port;
    private final String username;
    private final String password;  // TODO obfuscate the password
    // TODO add support for key files

    private transient Connection connection;

    @DataBoundConstructor
    public SSHLauncher(String host, int port, String username, String password) {
        this.host = host;
        this.port = port == 0 ? 22 : port;
        this.username = username;
        this.password = password;
    }

    public boolean isLaunchSupported() {
        return true;
    }

    /**
     * Gets the formatted current time stamp.
     */
    private static String getTimestamp() {
        return String.format("[%1$tD %1$tT]", new Date());
    }
    
    /**
     * Returns remote root workspace (without trailing slash)
     */
    private static String getWorkingDirectory(SlaveComputer computer) {
        String workingDirectory = computer.getNode().getRemoteFS();
        while (workingDirectory.endsWith("/")) {
            workingDirectory = workingDirectory.substring(0, workingDirectory.length() - 1);
        }
        return workingDirectory;
    }

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
            listener.getLogger().println("[SSH] Connection closed.");
        }
    }

    private void startSlave(SlaveComputer computer, final StreamTaskListener listener, String java, String workingDirectory) throws IOException {
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

    private void copySlaveJar(StreamTaskListener listener, String workingDirectory) throws IOException {
        String fileName = workingDirectory + "/slave.jar";

        listener.getLogger().println("[SSH] Starting sftp client...");
        SFTPv3Client sftpClient = null;
        try {
            sftpClient = new SFTPv3Client(connection);

            try {
                // TODO decide best permissions and handle errors if exists already
                sftpClient.mkdir(workingDirectory, 0700);

                // TODO handle the file existing already
                listener.getLogger().println("[SSH] Copying latest slave.jar...");
                SFTPv3FileHandle fileHandle = sftpClient.createFile(fileName);

                InputStream is = null;
                try {
                    // TODO get the slave jar the correct way... this may not be working
                    is = getClass().getResourceAsStream("/WEB-INF/slave.jar");
                    byte[] buf = new byte[2048];

                    listener.getLogger().println("[SSH] Sending data...");

                    int count = 0;
                    int bufsiz = 0;
                    try {
                        while ((bufsiz = is.read(buf)) != -1) {
                            sftpClient.write(fileHandle, (long) count, buf, 0, bufsiz);
                            count += bufsiz;
                        }
                        listener.getLogger().println("[SSH] Sent " + count + " bytes.");
                    } catch (Exception e) {
                        listener.getLogger().println("[SSH] Error writing to remote file");
                        e.printStackTrace(listener.getLogger());
                    }
                } finally {
                    if (is != null) {
                        is.close();
                    }
                }
            } catch (Exception e) {
                listener.getLogger().println("[SSH] Error creating file");
                e.printStackTrace(listener.getLogger());
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
        listener.getLogger().println("[SSH] Checking default java version");
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

                Outer:for (BufferedReader r : new BufferedReader[]{r1, r2}) {
                    while (null != (line = r.readLine())) {
                        if(line.startsWith("java version \"")) {
                            break Outer;
                        }
                        listener.getLogger().println("  " + line);
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
        listener.getLogger().println("[SSH] java version = " + line);

        // TODO make this version check a bit less hacky
        if (line.compareTo("1.5") < 0) {
            // TODO find a java that is at least 1.5
            throw new IOException("Could not find a version of java that is at least version 1.5");
        }
        return java;
    }

    private void openConnection(StreamTaskListener listener) throws IOException {
        listener.getLogger().println("[SSH] Opening SSH connection to " + host + ":" + port);
        connection.connect();

        // TODO if using a key file, use the key file instead of password
        listener.getLogger().println("[SSH] Authenticating as " + username + "/******");
        boolean isAuthenicated = connection.authenticateWithPassword(username, password);

        if (isAuthenicated && connection.isAuthenticationComplete()) {
            listener.getLogger().println("[SSH] Authentication successful.");
        } else {
            listener.getLogger().println("[SSH] Authentication failed.");
            connection.close();
            connection = null;
            listener.getLogger().println("[SSH] Connection closed.");
            throw new IOException("Authentication failed.");
        }
    }

    public synchronized void afterDisconnect(SlaveComputer slaveComputer, StreamTaskListener listener) {
        String workingDirectory = getWorkingDirectory(slaveComputer);
        String fileName = workingDirectory + "/slave.jar";
        
        if (connection != null) {

            SFTPv3Client sftpClient = null;
            try {
                sftpClient = new SFTPv3Client(connection);
                sftpClient.rm(fileName);
            } catch (Exception e) {
                listener.getLogger().println("[SSH] Error deleting file");
                e.printStackTrace(listener.getLogger());
            } finally {
                if (sftpClient != null) {
                    sftpClient.close();
                }
            }

            connection.close();
            connection = null;
            listener.getLogger().println("[SSH] Connection closed.");
        }
        super.afterDisconnect(slaveComputer, listener);
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public Descriptor<ComputerLauncher> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final Descriptor<ComputerLauncher> DESCRIPTOR = new DescriptorImpl();

    private static class DescriptorImpl extends Descriptor<ComputerLauncher> {

        protected DescriptorImpl() {
            super(SSHLauncher.class);
        }

        public String getDisplayName() {
            return "Launch slave agents on Linux machines via SSH";
        }
    }
}
