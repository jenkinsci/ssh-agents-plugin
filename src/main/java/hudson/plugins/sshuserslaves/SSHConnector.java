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
package hudson.plugins.sshuserslaves;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.trilead.ssh2.Connection;
import hudson.Extension;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import hudson.plugins.sshuserslaves.verifiers.SshHostKeyVerificationStrategy;
import hudson.security.ACL;
import hudson.slaves.ComputerConnector;
import hudson.slaves.ComputerConnectorDescriptor;
import hudson.tools.JDKInstaller;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import java.util.Collections;
import org.acegisecurity.Authentication;

import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

import static hudson.Util.fixEmpty;
import hudson.model.Computer;
import hudson.security.AccessControlled;
import org.kohsuke.stapler.QueryParameter;

/**
 * {@link ComputerConnector} for {@link SSHLauncher}.
 * <p>
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

    /**
     *  Field launchTimeoutSeconds.
     */
    public final Integer launchTimeoutSeconds;

    /**
     *  Field maxNumRetries.
     */
    public final Integer maxNumRetries;

    /**
     *  Field retryWaitTime.
     */
    public final Integer retryWaitTime;
    
    private final SshHostKeyVerificationStrategy sshHostKeyVerificationStrategy;

    public StandardUsernameCredentials getCredentials() {
        String credentialsId = this.credentialsId == null
                ? (this.credentials == null ? null : this.credentials.getId())
                : this.credentialsId;
        try {
            // only ever want from the system
            // lookup every time so that we always have the latest
            StandardUsernameCredentials credentials = 
                    credentialsId == null ? null :
                    SSHLauncher.lookupSystemCredentials(credentialsId);
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
     * @see SSHLauncher#SSHLauncher(String, int, String, String, String, String, String, Integer, Integer, Integer)
     */
    @Deprecated
    public SSHConnector(int port, String credentialsId, String jvmOptions, String javaPath,
                        String prefixStartSlaveCmd, String suffixStartSlaveCmd, Integer launchTimeoutSeconds,
                        Integer maxNumRetries, Integer retryWaitTime) {
        this(port, SSHLauncher.lookupSystemCredentials(credentialsId), null, null, null, jvmOptions, javaPath, null,
             prefixStartSlaveCmd, suffixStartSlaveCmd, launchTimeoutSeconds, maxNumRetries, retryWaitTime, null);
    }
    
    @DataBoundConstructor
    public SSHConnector(int port, String credentialsId, String jvmOptions, String javaPath,
                        String prefixStartSlaveCmd, String suffixStartSlaveCmd, Integer launchTimeoutSeconds,
                        Integer maxNumRetries, Integer retryWaitTime, SshHostKeyVerificationStrategy sshHostKeyVerificationStrategy) {
        this(port, SSHLauncher.lookupSystemCredentials(credentialsId), null, null, null, jvmOptions, javaPath, null,
             prefixStartSlaveCmd, suffixStartSlaveCmd, launchTimeoutSeconds, maxNumRetries, retryWaitTime, sshHostKeyVerificationStrategy);
    }

    /**
     * @see SSHLauncher#SSHLauncher(String, int, String, String, String, String, String, Integer)
     */
    @Deprecated
    public SSHConnector(int port, String credentialsId, String jvmOptions, String javaPath,
                        String prefixStartSlaveCmd, String suffixStartSlaveCmd, Integer launchTimeoutSeconds) {
        this(port, SSHLauncher.lookupSystemCredentials(credentialsId), null, null, null, jvmOptions, javaPath, null,
                prefixStartSlaveCmd, suffixStartSlaveCmd, launchTimeoutSeconds, null, null);
    }

    /**
     * @deprecated Use {@link SSHConnector#SSHConnector(int,String,String,String,String,String,Integer)}
     */
    @Deprecated
    public SSHConnector(int port, String credentialsId, String jvmOptions, String javaPath,
                        String prefixStartSlaveCmd, String suffixStartSlaveCmd) {
        this(port, SSHLauncher.lookupSystemCredentials(credentialsId), null, null, null, jvmOptions, javaPath, null,
                prefixStartSlaveCmd, suffixStartSlaveCmd, null, null, null);
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
                        String javaPath, String prefixStartSlaveCmd, String suffixStartSlaveCmd) {
        this(port, null, username, password, privatekey, jvmOptions, javaPath, null, prefixStartSlaveCmd,
                suffixStartSlaveCmd
        );
    }

    /**
     * @see SSHLauncher#SSHLauncher(String, int, String, String, String, String, String, JDKInstaller, String, String)
     */
    public SSHConnector(int port, String username, String password, String privatekey,
                        String jvmOptions, String javaPath, JDKInstaller jdkInstaller, String prefixStartSlaveCmd,
                        String suffixStartSlaveCmd) {
        this(port, null, username, password, privatekey, jvmOptions, javaPath, jdkInstaller, prefixStartSlaveCmd,
                suffixStartSlaveCmd);
    }

    /**
     * @see SSHLauncher#SSHLauncher(String, int, StandardUsernameCredentials, String, String, JDKInstaller, String,
     * String)
     */
    public SSHConnector(int port, StandardUsernameCredentials credentials, String username, String password,
                        String privatekey, String jvmOptions, String javaPath, JDKInstaller jdkInstaller,
                        String prefixStartSlaveCmd, String suffixStartSlaveCmd) {
        this(port, credentials, username, password, privatekey, jvmOptions, javaPath, jdkInstaller, prefixStartSlaveCmd,
                suffixStartSlaveCmd, null, null, null);
    }
    /**
     * @see SSHLauncher#SSHLauncher(String, int, StandardUsernameCredentials, String, String, JDKInstaller, String,
     * String)
     */
    @Deprecated
    public SSHConnector(int port, StandardUsernameCredentials credentials, String username, String password,
                        String privatekey, String jvmOptions, String javaPath, JDKInstaller jdkInstaller,
                        String prefixStartSlaveCmd, String suffixStartSlaveCmd, Integer launchTimeoutSeconds,
                        Integer maxNumRetries, Integer retryWaitTime) {
        this(port, credentials, username, password, privatekey, jvmOptions, javaPath, jdkInstaller, prefixStartSlaveCmd,
                suffixStartSlaveCmd, null, null, null, null);
    }
        
    /**
     * @see SSHLauncher#SSHLauncher(String, int, StandardUsernameCredentials, String, String, JDKInstaller, String,
     * String)
     */
    public SSHConnector(int port, StandardUsernameCredentials credentials, String username, String password,
                        String privatekey, String jvmOptions, String javaPath, JDKInstaller jdkInstaller,
                        String prefixStartSlaveCmd, String suffixStartSlaveCmd, Integer launchTimeoutSeconds,
                        Integer maxNumRetries, Integer retryWaitTime, SshHostKeyVerificationStrategy sshHostKeyVerificationStrategy) {
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
        this.launchTimeoutSeconds = launchTimeoutSeconds == null || launchTimeoutSeconds <= 0 ? null : launchTimeoutSeconds;
        this.maxNumRetries = maxNumRetries != null && maxNumRetries > 0 ? maxNumRetries : 0;
        this.retryWaitTime = retryWaitTime != null && retryWaitTime > 0 ? retryWaitTime : 0;
        this.sshHostKeyVerificationStrategy = sshHostKeyVerificationStrategy;
    }

    @Override
    public SSHLauncher launch(String host, TaskListener listener) throws IOException, InterruptedException {
        return new SSHLauncher(host, port, getCredentials(), jvmOptions, javaPath, jdkInstaller, prefixStartSlaveCmd,
                suffixStartSlaveCmd, launchTimeoutSeconds, maxNumRetries, retryWaitTime, sshHostKeyVerificationStrategy);
    }
    
    public SshHostKeyVerificationStrategy getSshHostKeyVerificationStrategy() {
    	return sshHostKeyVerificationStrategy;
    }

    @Extension
    public static class DescriptorImpl extends ComputerConnectorDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.SSHLauncher_DescriptorDisplayName();
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context, @QueryParameter String credentialsId) {
            AccessControlled _context = (context instanceof AccessControlled ? (AccessControlled) context : Jenkins.getInstance());
            if (_context == null || !_context.hasPermission(Computer.CONFIGURE)) {
                return new StandardUsernameListBoxModel()
                        .includeCurrentValue(credentialsId);
            }
            Authentication authentication = Jenkins.getAuthentication();
            return new StandardUsernameListBoxModel()
                    .includeMatchingAs(
                        authentication,
                            context,
                            StandardUsernameCredentials.class,
                            Collections.singletonList(SSHLauncher.SSH_SCHEME),
                            SSHAuthenticator.matcher(Connection.class)
                    )
                    .includeCurrentValue(credentialsId);
        }

        public FormValidation doCheckCredentialsId(@AncestorInPath ItemGroup context,
                                                   @QueryParameter String value) {
            Authentication authentication = Jenkins.getAuthentication();
            AccessControlled _context =
                    (context instanceof AccessControlled ? (AccessControlled) context : Jenkins.getInstance());
            if (_context == null || !_context.hasPermission(Computer.CONFIGURE)) {
                return FormValidation.ok(); // no need to alarm a user that cannot configure
            }
            for (ListBoxModel.Option o : CredentialsProvider.listCredentials(StandardUsernameCredentials.class, context, authentication,
                    Collections.singletonList(SSHLauncher.SSH_SCHEME),
                    SSHAuthenticator.matcher(Connection.class))) {
                if (StringUtils.equals(value, o.value)) {
                    return FormValidation.ok();
                }
            }
            return FormValidation.error(Messages.SSHLauncher_SelectedCredentialsMissing());
        }

        public FormValidation doCheckLaunchTimeoutSeconds(String value) {
            if (StringUtils.isBlank(value)) return FormValidation.ok();
            try {
                if (Integer.parseInt(value.trim()) < 0) {
                    return FormValidation.error(Messages.SSHConnector_LaunchTimeoutMustBePositive());
                }
                return FormValidation.ok();
            } catch (NumberFormatException e) {
                return FormValidation.error(Messages.SSHConnector_LaunchTimeoutMustBeANumber());
            }
        }

    }
}
