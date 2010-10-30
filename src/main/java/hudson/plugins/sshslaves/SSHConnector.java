package hudson.plugins.sshslaves;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.slaves.ComputerConnector;
import hudson.slaves.ComputerConnectorDescriptor;
import hudson.util.Secret;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

import static hudson.Util.*;

/**
 * {@link ComputerConnector} for {@link SSHLauncher}.
 *
 * <p>
 * Significant code duplication between this and {@link SSHLauncher} because of the historical reason.
 * Newer plugins like this should define a separate Describable connection parameter class and have
 * connector and launcher share them.
 *
 * @author Kohsuke Kawaguchi
 */
public class SSHConnector extends ComputerConnector {
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
    private final Secret password;

    /**
     * File path of the private key.
     */
    private final String privatekey;

    /**
     * Field jvmOptions.
     */
    private final String jvmOptions;

    /**
     * Constructor SSHLauncher creates a new SSHLauncher instance.
     *
     * @param port       The port to connect on.
     * @param username   The username to connect as.
     * @param password   The password to connect with.
     * @param privatekey The ssh privatekey to connect with.
     * @param jvmOptions
     */
    @DataBoundConstructor
    public SSHConnector(int port, String username, String password, String privatekey, String jvmOptions) {
        this.jvmOptions = jvmOptions;
        this.port = port == 0 ? 22 : port;
        this.username = username;
        this.password = Secret.fromString(fixEmpty(password));
        this.privatekey = privatekey;
    }

    @Override
    public SSHLauncher launch(String host, TaskListener listener) throws IOException, InterruptedException {
        return new SSHLauncher(host,port,username,Secret.toString(password),privatekey,jvmOptions);
    }

    @Extension
    public static class DescriptorImpl extends ComputerConnectorDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.SSHLauncher_DescriptorDisplayName();
        }
    }
}
