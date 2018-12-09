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

import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import hudson.Extension;
import hudson.model.TaskListener;
import hudson.plugins.sshslaves.verifiers.NonVerifyingKeyVerificationStrategy;
import hudson.plugins.sshslaves.verifiers.SshHostKeyVerificationStrategy;
import hudson.security.AccessControlled;
import hudson.slaves.ComputerConnector;
import hudson.slaves.ComputerConnectorDescriptor;
import hudson.tools.JDKInstaller;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.util.logging.Logger;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.commons.lang.StringUtils;
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
 * NOTE: if need, default launcher values will be applied when we create the SSHLauncher
 * on {@link #launch(String, TaskListener)} method. It means it is not need to initialized default values on
 * SSHConnector constructors and setters.
 *
 * @author Kohsuke Kawaguchi
 */
public class SSHConnector extends ComputerConnector {
    private static final Logger LOGGER = Logger.getLogger(ComputerConnector.class.getName());

    /**
     * Field port
     */
    public int port;

    /**
     * The id of the credentials to use.
     */
    private String credentialsId;

    /**
     * Field jvmOptions.
     */
    public String jvmOptions;

    /**
     * Field javaPath.
     */
    public String javaPath;

    /**
     * Field prefixStartSlaveCmd.
     */
    public String prefixStartSlaveCmd;

    /**
     * Field suffixStartSlaveCmd.
     */
    public String suffixStartSlaveCmd;

    /**
     *  Field launchTimeoutSeconds.
     */
    public Integer launchTimeoutSeconds;

    /**
     *  Field maxNumRetries.
     */
    public Integer maxNumRetries;

    /**
     *  Field retryWaitTime.
     */
    public Integer retryWaitTime;

    /**
     * The verifier to use for checking the SSH key presented by the host
     * responding to the connection
     */
    private SshHostKeyVerificationStrategy sshHostKeyVerificationStrategy;

    /**
     * Allow to enable/disable the TCP_NODELAY flag on the SSH connection.
     */
    private Boolean tcpNoDelay;

    /**
     * Set the value to add to the remoting parameter -workDir
     * @see <a href="https://github.com/jenkinsci/remoting/blob/master/docs/workDir.md#remoting-work-directory">Remoting Work directory</a>
     */
    private String workDir;

    /**
     * @see SSHLauncher#SSHLauncher
     */
    @DataBoundConstructor
    public SSHConnector(@NonNull String credentialsId,
                        @NonNull SshHostKeyVerificationStrategy sshHostKeyVerificationStrategy){
        this.credentialsId = credentialsId;
        this.sshHostKeyVerificationStrategy = sshHostKeyVerificationStrategy;
    }

    /**
     * @see SSHLauncher#SSHLauncher
     * @deprecated use {@link #SSHConnector(String credentialsId, SshHostKeyVerificationStrategy sshHostKeyVerificationStrategy) } and setter methods instead.
     */
    public SSHConnector(int port, String credentialsId, String jvmOptions, String javaPath,
                        String prefixStartSlaveCmd, String suffixStartSlaveCmd, Integer launchTimeoutSeconds,
                        Integer maxNumRetries, Integer retryWaitTime, SshHostKeyVerificationStrategy sshHostKeyVerificationStrategy) {
        this(credentialsId, sshHostKeyVerificationStrategy);

        setPort(port);
        setJvmOptions(jvmOptions);
        setJavaPath(javaPath);
        setPrefixStartSlaveCmd(prefixStartSlaveCmd);
        setSuffixStartSlaveCmd(suffixStartSlaveCmd);
        setLaunchTimeoutSeconds(launchTimeoutSeconds);
        setMaxNumRetries(maxNumRetries);
        setRetryWaitTime(retryWaitTime);
        LOGGER.warning("This constructor is deprecated and will be removed on next versions, please do not use it.");
    }

    /**
     * @see SSHLauncher#SSHLauncher
     * @deprecated use {@link #SSHConnector(String credentialsId, SshHostKeyVerificationStrategy sshHostKeyVerificationStrategy) } and setter methods instead.
     */
    public SSHConnector(int port, StandardUsernameCredentials credentials, String username, String password,
                        String privatekey, String jvmOptions, String javaPath, JDKInstaller jdkInstaller,
                        String prefixStartSlaveCmd, String suffixStartSlaveCmd) {
        this(credentials.getId(), new NonVerifyingKeyVerificationStrategy());
        setPort(port);
        setJvmOptions(jvmOptions);
        setJavaPath(javaPath);
        setPrefixStartSlaveCmd(prefixStartSlaveCmd);
        setSuffixStartSlaveCmd(suffixStartSlaveCmd);
        LOGGER.warning("This constructor is deprecated and will be removed on next versions, please do not use it.");
    }

    /**
     * @see SSHLauncher#SSHLauncher
     * @deprecated use {@link #SSHConnector(String credentialsId, SshHostKeyVerificationStrategy sshHostKeyVerificationStrategy) } and setter methods instead.
     */
    public SSHConnector(int port,@NonNull StandardUsernameCredentials credentials, String username, String password,
                        String privatekey, String jvmOptions, String javaPath, JDKInstaller jdkInstaller,
                        String prefixStartSlaveCmd, String suffixStartSlaveCmd, Integer launchTimeoutSeconds,
                        Integer maxNumRetries, Integer retryWaitTime, SshHostKeyVerificationStrategy sshHostKeyVerificationStrategy) {
        this(credentials.getId(), sshHostKeyVerificationStrategy);

        setPort(port);
        setJvmOptions(jvmOptions);
        setJavaPath(javaPath);
        setPrefixStartSlaveCmd(prefixStartSlaveCmd);
        setSuffixStartSlaveCmd(suffixStartSlaveCmd);
        setLaunchTimeoutSeconds(launchTimeoutSeconds);
        setMaxNumRetries(maxNumRetries);
        setRetryWaitTime(retryWaitTime);
        LOGGER.warning("This constructor is deprecated and will be removed on next versions, please do not use it.");
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public SshHostKeyVerificationStrategy getSshHostKeyVerificationStrategy() {
        return sshHostKeyVerificationStrategy;
    }

    public int getPort() {
        return port;
    }

    @DataBoundSetter
    public void setPort(int port) {
        this.port = port;
    }

    public String getJvmOptions() {
        return jvmOptions;
    }

    @DataBoundSetter
    public void setJvmOptions(String jvmOptions) {
        this.jvmOptions = jvmOptions;
    }

    public String getJavaPath() {
        return javaPath;
    }

    @DataBoundSetter
    public void setJavaPath(String javaPath) {
        this.javaPath = javaPath;
    }

    public String getPrefixStartSlaveCmd() {
        return prefixStartSlaveCmd;
    }

    @DataBoundSetter
    public void setPrefixStartSlaveCmd(String prefixStartSlaveCmd) {
        this.prefixStartSlaveCmd = prefixStartSlaveCmd;
    }

    public String getSuffixStartSlaveCmd() {
        return suffixStartSlaveCmd;
    }

    @DataBoundSetter
    public void setSuffixStartSlaveCmd(String suffixStartSlaveCmd) {
        this.suffixStartSlaveCmd = suffixStartSlaveCmd;
    }

    public Integer getLaunchTimeoutSeconds() {
        return launchTimeoutSeconds;
    }

    @DataBoundSetter
    public void setLaunchTimeoutSeconds(Integer launchTimeoutSeconds) {
        this.launchTimeoutSeconds = launchTimeoutSeconds;
    }

    public Integer getMaxNumRetries() {
        return maxNumRetries;
    }

    @DataBoundSetter
    public void setMaxNumRetries(Integer maxNumRetries) {
        this.maxNumRetries = maxNumRetries;
    }

    public Integer getRetryWaitTime() {
        return retryWaitTime;
    }

    @DataBoundSetter
    public void setRetryWaitTime(Integer retryWaitTime) {
        this.retryWaitTime = retryWaitTime;
    }

    public Boolean getTcpNoDelay() {
        return tcpNoDelay;
    }

    @DataBoundSetter
    public void setTcpNoDelay(Boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public String getWorkDir() {
        return workDir;
    }

    @DataBoundSetter
    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }

    @Override
    public SSHLauncher launch(String host, TaskListener listener) {
        SSHLauncher launcher = new SSHLauncher(host, this.credentialsId, sshHostKeyVerificationStrategy);
        launcher.setPort(port);
        launcher.setJvmOptions(jvmOptions);
        launcher.setJavaPath(javaPath);
        launcher.setPrefixStartSlaveCmd(prefixStartSlaveCmd);
        launcher.setSuffixStartSlaveCmd(suffixStartSlaveCmd);
        launcher.setLaunchTimeoutSeconds(launchTimeoutSeconds);
        launcher.setMaxNumRetries(maxNumRetries);
        launcher.setRetryWaitTime(retryWaitTime);
        launcher.setTcpNoDelay(tcpNoDelay);
        launcher.setWorkDir(workDir);
        return launcher;
    }

    @Extension
    public static class DescriptorImpl extends ComputerConnectorDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.SSHLauncher_DescriptorDisplayName();
        }

        @RequirePOST
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath AccessControlled context, @QueryParameter String credentialsId) {
            return SSHCredentialsManager.fillCredentialsIdItems(context, credentialsId);
        }

        @RequirePOST
        public FormValidation doCheckCredentialsId(@AncestorInPath AccessControlled context, @QueryParameter String value) {
            return SSHCredentialsManager.checkCredentialId(context, value);
        }

        public FormValidation doCheckLaunchTimeoutSeconds(String value) {
            if (StringUtils.isBlank(value)){
                return FormValidation.ok();
            }
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
