package hudson.plugins.sshslaves;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.slaves.ComputerConnector;
import hudson.slaves.ComputerConnectorDescriptor;
import hudson.tools.JDKInstaller;
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
    public final int port;

    /**
     * Field username
     */
    public final String username;

    /**
     * Field password
     *
     * @todo remove password once authentication is stored in the descriptor.
     */
    public final Secret password;

    /**
     * File path of the private key.
     */
    public final String privatekey;

    /**
     * Field jvmOptions.
     */
    public final String jvmOptions;

    /**
     * Field javaPath.
     */
    public final String javaPath;


    /**
     * Field jdk
     */
    public final JDKInstaller jdkInstaller;

    /**
     * Field prefixStartSlaveCmd. 
     */
    public final String prefixStartSlaveCmd;
    
    /**
     *  Field suffixStartSlaveCmd.
     */
    public final String suffixStartSlaveCmd;
    

    /**
     * @see SSHLauncher#SSHLauncher(String, int, String, String, String, String, String, String, String)
     */
    @DataBoundConstructor
    public SSHConnector(int port, String username, String password, String privatekey, String jvmOptions, String javaPath, 
                                                                   String prefixStartSlaveCmd, String suffixStartSlaveCmd) {
        this(port, username, password, privatekey, jvmOptions, javaPath, null, prefixStartSlaveCmd, suffixStartSlaveCmd);
    }

    /**
     * @see SSHLauncher#SSHLauncher(String, int, String, String, String, String, String, JDKInstaller, prefixStartSlaveCmd, suffixStartSlaveCmd)
     */
    public SSHConnector(int port, String username, String password, String privatekey, String jvmOptions, String javaPath, 
                                        JDKInstaller jdkInstaller, String prefixStartSlaveCmd, String suffixStartSlaveCmd) {
        this.jvmOptions = jvmOptions;
        this.port = port == 0 ? 22 : port;
        this.username = username;
        this.password = Secret.fromString(fixEmpty(password));
        this.privatekey = privatekey;
        this.javaPath = javaPath;
        this.jdkInstaller = jdkInstaller;
        this.prefixStartSlaveCmd = fixEmpty(prefixStartSlaveCmd);
        this.suffixStartSlaveCmd = fixEmpty(suffixStartSlaveCmd);
    }

    @Override
    public SSHLauncher launch(String host, TaskListener listener) throws IOException, InterruptedException {
        return new SSHLauncher(host,port,username,Secret.toString(password),privatekey,jvmOptions,javaPath,jdkInstaller, prefixStartSlaveCmd, suffixStartSlaveCmd);
    }

    @Extension
    public static class DescriptorImpl extends ComputerConnectorDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.SSHLauncher_DescriptorDisplayName();
        }
    }
}
