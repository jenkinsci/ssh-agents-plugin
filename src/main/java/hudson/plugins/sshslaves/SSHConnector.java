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
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserListBoxModel;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.trilead.ssh2.Connection;
import hudson.Extension;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.slaves.ComputerConnector;
import hudson.slaves.ComputerConnectorDescriptor;
import hudson.tools.JDKInstaller;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

import static hudson.Util.fixEmpty;

/**
 * {@link ComputerConnector} for {@link SSHLauncher}.
 * <p/>
 * <p/>
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
     * The id of the credentials to use.
     */
    private String credentialsId;

    /**
     * Transient stash of the credentials to use, mostly just for providing floating user object.
     */
    private transient StandardUsernameCredentials credentials;

    /**
     * Field username
     *
     * @deprecated
     */
    @Deprecated
    public transient String username;

    /**
     * Field password
     *
     * @deprecated
     */
    @Deprecated
    public transient Secret password;

    /**
     * File path of the private key.
     *
     * @deprecated
     */
    @Deprecated
    public transient String privatekey;

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
     * Field suffixStartSlaveCmd.
     */
    public final String suffixStartSlaveCmd;

    public StandardUsernameCredentials getCredentials() {
        String credentialsId = this.credentialsId == null
                ? (this.credentials == null ? null : this.credentials.getId())
                : this.credentialsId;
        try {
            // only ever want from the system
            // lookup every time so that we always have the latest
            StandardUsernameCredentials credentials = SSHLauncher.lookupSystemCredentials(credentialsId);
            if (credentials != null) {
                this.credentials = credentials;
                return credentials;
            }
        } catch (Throwable t) {
            // ignore
        }
        if (credentials == null) {
            if (credentialsId == null) {
                credentials = SSHLauncher.upgrade(username, password, privatekey, null);
                this.credentialsId = credentials.getId();
            }
        }

        return credentials;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    /**
     * @see SSHLauncher#SSHLauncher(String, int, StandardUsernameCredentials, String, String, String, String)
     */
    @DataBoundConstructor
    public SSHConnector(int port, String credentialsId, String jvmOptions, String javaPath,
                        String prefixStartSlaveCmd, String suffixStartSlaveCmd) {
        this(port, SSHLauncher.lookupSystemCredentials(credentialsId), null, null, null, jvmOptions, javaPath, null,
                prefixStartSlaveCmd, suffixStartSlaveCmd
        );
    }

    /**
     * @see SSHLauncher#SSHLauncher(String, int, StandardUsernameCredentials, String, String, String, String)
     */
    public SSHConnector(int port, StandardUsernameCredentials credentials, String jvmOptions, String javaPath,
                        String prefixStartSlaveCmd, String suffixStartSlaveCmd) {
        this(port, credentials, null, null, null, jvmOptions, javaPath, null, prefixStartSlaveCmd, suffixStartSlaveCmd
        );
    }

    /**
     * @see SSHLauncher#SSHLauncher(String, int, String, String, String, String, String, String, String)
     */
    public SSHConnector(int port, String username, String password, String privatekey, String jvmOptions,
                        String javaPath,
                        String prefixStartSlaveCmd, String suffixStartSlaveCmd) {
        this(port, null, username, password, privatekey, jvmOptions, javaPath, null, prefixStartSlaveCmd,
                suffixStartSlaveCmd
        );
    }

    /**
     * @see SSHLauncher#SSHLauncher(String, int, String, String, String, String, String, JDKInstaller, String, String)
     */
    public SSHConnector(int port, String username, String password, String privatekey,
                        String jvmOptions,
                        String javaPath,
                        JDKInstaller jdkInstaller, String prefixStartSlaveCmd, String suffixStartSlaveCmd) {
        this(port, null, username, password, privatekey, jvmOptions, javaPath, jdkInstaller, prefixStartSlaveCmd,
                suffixStartSlaveCmd);
    }

    /**
     * @see SSHLauncher#SSHLauncher(String, int, StandardUsernameCredentials, String, String, JDKInstaller, String,
     * String)
     */
    public SSHConnector(int port, StandardUsernameCredentials credentials, String username, String password,
                        String privatekey,
                        String jvmOptions,
                        String javaPath,
                        JDKInstaller jdkInstaller, String prefixStartSlaveCmd, String suffixStartSlaveCmd) {
        this.jvmOptions = jvmOptions;
        this.port = port == 0 ? 22 : port;
        this.credentials = credentials;
        this.credentialsId = credentials == null ? null : this.credentials.getId();
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
        return new SSHLauncher(host, port, getCredentials(), jvmOptions, javaPath, jdkInstaller, prefixStartSlaveCmd,
                suffixStartSlaveCmd);
    }

    @Extension
    public static class DescriptorImpl extends ComputerConnectorDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.SSHLauncher_DescriptorDisplayName();
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context) {
            return new SSHUserListBoxModel().withMatching(SSHAuthenticator.matcher(Connection.class),
                    CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class, context,
                            ACL.SYSTEM, SSHLauncher.SSH_SCHEME));
        }

    }
}
