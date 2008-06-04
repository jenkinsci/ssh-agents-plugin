package hudson.plugins.sshslaves;

import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import hudson.util.StreamTaskListener;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A computer launcher that tries to start a linux slave by opening an SSH connection and trying to find java.
 */
public class SSHLauncher extends ComputerLauncher {

    private final String host;
    private final int port;
    private final String username;
    private final String password;  // TODO obfuscate the password
    // TODO add support for key files

    @DataBoundConstructor
    public SSHLauncher(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public boolean isLaunchSupported() {
        return true;
    }

    public void launch(SlaveComputer slaveComputer, StreamTaskListener streamTaskListener) {
        // TODO open ssh connection to the host

        // TODO check if the default java is 1.5 or newer

        // TODO if not, find a java that is or throw an error

        // TODO copy the slave.jar using sftp

        // TODO launch the slave.jar with a command that removes it once it terminates

        // TODO set the channel to the STD I/O of the ssh connection
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
