package hudson.plugins.sshslaves;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.Session;
import com.trilead.ssh2.StreamGobbler;
import hudson.model.Descriptor;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import hudson.util.StreamTaskListener;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

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

    public synchronized void launch(SlaveComputer slaveComputer, StreamTaskListener listener) {
        connection = new Connection(host, port);

        try {
            // TODO open ssh connection to the host
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
                return;
            }

            listener.getLogger().println("[SSH] Checking default java version");
            String line;
            Session session = connection.openSession();
            try {
                session.execCommand("java -version");
                StreamGobbler out = new StreamGobbler(session.getStdout());
                StreamGobbler err = new StreamGobbler(session.getStderr());
                try {
                    BufferedReader r = new BufferedReader(new InputStreamReader(out));

                    // TODO make sure this works with IBM JVM & JRocket

                    while (null != (line = r.readLine()) && !line.startsWith("java version \"")) {
                        listener.getLogger().println("  " + line);
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

            // TODO check if the default java is 1.5 or newer

            // TODO if not, find a java that is or throw an error

            // TODO copy the slave.jar using sftp

            // TODO launch the slave.jar with a command that removes it once it terminates

            // TODO set the channel to the STD I/O of the ssh connection

            throw new IOException("Implementation not yet finished");
        } catch (IOException e) {
            e.printStackTrace(listener.getLogger());
            connection.close();
            connection = null;
            listener.getLogger().println("[SSH] Connection closed.");
        }


    }

    public synchronized void afterDisconnect(SlaveComputer slaveComputer, StreamTaskListener listener) {
        if (connection != null) {

            // TODO remove slave.jar

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
