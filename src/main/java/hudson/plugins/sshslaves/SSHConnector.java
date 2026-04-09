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

import static hudson.Util.fixEmpty;
import static hudson.Util.fixEmptyAndTrim;
import static hudson.plugins.sshslaves.SSHLauncher.*;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.trilead.ssh2.Connection;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import hudson.plugins.sshslaves.verifiers.SshHostKeyVerificationStrategy;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.slaves.ComputerConnector;
import hudson.slaves.ComputerConnectorDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.util.Collections;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * {@link ComputerConnector} for {@link SSHLauncher}.
 * <p>
 * <p>
 * Significant code duplication between this and {@link SSHLauncher} because of the historical reason.
 * Newer plugins like this should define a separate Describable connection parameter class and have
 * connector and launcher share them.
 *
 * @author Kohsuke Kawaguchi
 * @deprecated Use {@link hudson.plugins.sshslaves.mina.MinaSSHLauncher} instead.
 */
@Deprecated
public class SSHConnector extends ComputerConnector {
    /**
     * Field port
     */
    @SuppressFBWarnings(value = "PA_PUBLIC_PRIMITIVE_ATTRIBUTE", justification = "Preserve API compatibility.")
    public int port;

    /**
     * The id of the credentials to use.
     */
    private String credentialsId;

    /**
     * Field jvmOptions.
     */
    private String jvmOptions;

    /**
     * Field javaPath.
     */
    private String javaPath;

    /**
     * Field prefixStartSlaveCmd.
     */
    private String prefixStartSlaveCmd;

    /**
     * Field suffixStartSlaveCmd.
     */
    private String suffixStartSlaveCmd;

    /**
     *  Field launchTimeoutSeconds.
     */
    private Integer launchTimeoutSeconds;

    /**
     *  Field maxNumRetries.
     */
    private Integer maxNumRetries;

    /**
     *  Field retryWaitTime.
     */
    private Integer retryWaitTime;

    private SshHostKeyVerificationStrategy sshHostKeyVerificationStrategy;

    /**
     * Set the value to add to the remoting parameter -workDir
     * @see <a href="https://github.com/jenkinsci/remoting/blob/master/docs/workDir.md#remoting-work-directory">Remoting Work directory</a>
     */
    private String workDir;

    /**
     *  Field tcpNoDelay.
     */
    private Boolean tcpNoDelay;

    /**
     * Constructor SSHLauncher creates a new SSHLauncher instance.
     *
     * @param port       The port to connect on.
     * @param credentialsId The credentials id to connect as.
     */
    @DataBoundConstructor
    public SSHConnector(int port, String credentialsId) {
        setPort(port);
        this.credentialsId = credentialsId;
    }

    /**
     * Constructor SSHConnector creates a new SSHConnector instance.
     *
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
    public SSHConnector(
            int port,
            String credentialsId,
            String jvmOptions,
            String javaPath,
            String prefixStartSlaveCmd,
            String suffixStartSlaveCmd,
            Integer launchTimeoutSeconds,
            Integer maxNumRetries,
            Integer retryWaitTime,
            SshHostKeyVerificationStrategy sshHostKeyVerificationStrategy) {
        setJvmOptions(jvmOptions);
        setPort(port);
        this.credentialsId = credentialsId;
        setJavaPath(javaPath);
        setPrefixStartSlaveCmd(prefixStartSlaveCmd);
        setSuffixStartSlaveCmd(suffixStartSlaveCmd);

        this.sshHostKeyVerificationStrategy = sshHostKeyVerificationStrategy;

        setLaunchTimeoutSeconds(launchTimeoutSeconds);
        setMaxNumRetries(maxNumRetries);
        setRetryWaitTime(retryWaitTime);
    }

    @Override
    public SSHLauncher launch(@NonNull String host, TaskListener listener) {
        SSHLauncher sshLauncher = new SSHLauncher(
                host,
                port,
                credentialsId,
                jvmOptions,
                javaPath,
                prefixStartSlaveCmd,
                suffixStartSlaveCmd,
                launchTimeoutSeconds,
                maxNumRetries,
                retryWaitTime,
                sshHostKeyVerificationStrategy);
        sshLauncher.setWorkDir(workDir);
        sshLauncher.setTcpNoDelay(getTcpNoDelay());
        return sshLauncher;
    }

    @DataBoundSetter
    public void setJvmOptions(String value) {
        this.jvmOptions = fixEmpty(value);
    }

    @DataBoundSetter
    public void setJavaPath(String value) {
        this.javaPath = fixEmpty(value);
    }

    @DataBoundSetter
    public void setPrefixStartSlaveCmd(String value) {
        this.prefixStartSlaveCmd = fixEmpty(value);
    }

    @DataBoundSetter
    public void setSuffixStartSlaveCmd(String value) {
        this.suffixStartSlaveCmd = fixEmpty(value);
    }

    @DataBoundSetter
    public void setMaxNumRetries(Integer value) {
        this.maxNumRetries = value != null && value >= 0 ? value : DEFAULT_MAX_NUM_RETRIES;
    }

    @DataBoundSetter
    public void setLaunchTimeoutSeconds(Integer value) {
        this.launchTimeoutSeconds = value == null || value <= 0 ? DEFAULT_LAUNCH_TIMEOUT_SECONDS : value;
    }

    @DataBoundSetter
    public void setRetryWaitTime(Integer value) {
        this.retryWaitTime = value != null && value >= 0 ? value : DEFAULT_RETRY_WAIT_TIME;
    }

    @DataBoundSetter
    public void setSshHostKeyVerificationStrategy(SshHostKeyVerificationStrategy value) {
        this.sshHostKeyVerificationStrategy = value;
    }

    public void setPort(int value) {
        this.port = value == 0 ? DEFAULT_SSH_PORT : value;
    }

    @DataBoundSetter
    public void setTcpNoDelay(Boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public SshHostKeyVerificationStrategy getSshHostKeyVerificationStrategy() {
        return sshHostKeyVerificationStrategy;
    }

    @DataBoundSetter
    public void setWorkDir(String workDir) {
        this.workDir = fixEmptyAndTrim(workDir);
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public int getPort() {
        return port;
    }

    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public String getJvmOptions() {
        return jvmOptions;
    }

    public String getJavaPath() {
        return javaPath;
    }

    public String getPrefixStartSlaveCmd() {
        return prefixStartSlaveCmd;
    }

    public String getSuffixStartSlaveCmd() {
        return suffixStartSlaveCmd;
    }

    public Integer getLaunchTimeoutSeconds() {
        return launchTimeoutSeconds;
    }

    public Integer getMaxNumRetries() {
        return maxNumRetries;
    }

    public Integer getRetryWaitTime() {
        return retryWaitTime;
    }

    public String getWorkDir() {
        return workDir;
    }

    public Boolean getTcpNoDelay() {
        return tcpNoDelay != null ? tcpNoDelay : true;
    }

    @Extension
    public static class DescriptorImpl extends ComputerConnectorDescriptor {
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.SSHLauncher_DescriptorDisplayName();
        }

        @RequirePOST
        public ListBoxModel doFillCredentialsIdItems(
                @AncestorInPath ItemGroup context, @QueryParameter String credentialsId) {
            AccessControlled _context =
                    (context instanceof AccessControlled ? (AccessControlled) context : Jenkins.get());
            if (_context == null || !_context.hasPermission(Computer.CONFIGURE)) {
                return new StandardUsernameListBoxModel().includeCurrentValue(credentialsId);
            }
            return new StandardUsernameListBoxModel()
                    .includeMatchingAs(
                            ACL.SYSTEM2,
                            context,
                            StandardUsernameCredentials.class,
                            Collections.singletonList(SSHLauncher.SSH_SCHEME),
                            SSHAuthenticator.matcher(Connection.class))
                    .includeCurrentValue(credentialsId);
        }

        @RequirePOST
        public FormValidation doCheckCredentialsId(@AncestorInPath ItemGroup context, @QueryParameter String value) {
            AccessControlled _context =
                    (context instanceof AccessControlled ? (AccessControlled) context : Jenkins.get());
            if (_context == null || !_context.hasPermission(Computer.CONFIGURE)) {
                return FormValidation.ok(); // no need to alarm a user that cannot configure
            }
            for (ListBoxModel.Option o : CredentialsProvider.listCredentialsInItemGroup(
                    StandardUsernameCredentials.class,
                    context,
                    ACL.SYSTEM2,
                    Collections.singletonList(SSHLauncher.SSH_SCHEME),
                    SSHAuthenticator.matcher(Connection.class))) {
                if (StringUtils.equals(value, o.value)) {
                    return FormValidation.ok();
                }
            }
            return FormValidation.error(Messages.SSHLauncher_SelectedCredentialsMissing());
        }

        @RequirePOST
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
