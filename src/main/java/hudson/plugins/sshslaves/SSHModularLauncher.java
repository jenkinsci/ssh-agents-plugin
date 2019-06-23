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

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.trilead.ssh2.Connection;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.plugins.sshslaves.verifiers.SshHostKeyVerificationStrategy;
import hudson.plugins.sshslaves.verifiers.NonVerifyingKeyVerificationStrategy;
import hudson.security.AccessControlled;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.SlaveComputer;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.NamingThreadFactory;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.lang.InterruptedException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import org.kohsuke.stapler.interceptor.RequirePOST;
import static hudson.Util.*;
import static hudson.plugins.sshslaves.SSHLauncherConfig.getTimestamp;

/**
 * A computer launcher that tries to start a linux agent by opening an SSH connection and trying to find java.
 */
public class SSHModularLauncher extends ComputerLauncher implements SSHLauncherConfig {

    private static final Logger LOGGER = Logger.getLogger(SSHModularLauncher.class.getName());

    public static final Integer DEFAULT_MAX_NUM_RETRIES = 10;
    public static final Integer DEFAULT_RETRY_WAIT_TIME = 15;
    public static final Integer DEFAULT_LAUNCH_TIMEOUT_SECONDS = DEFAULT_MAX_NUM_RETRIES * DEFAULT_RETRY_WAIT_TIME + 60;
    public static final String AGENT_JAR = "remoting.jar";
    public static final String SLASH_AGENT_JAR = "/" + AGENT_JAR;
    public static final String WORK_DIR_PARAM = " -workDir ";
    public static final int DEFAULT_SSH_PORT = 22;

    /**
     * Field host
     */
    private final String host;

    /**
     * Field port
     */
    private int port;

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
    public String javaPath;

    /**
     * Indicates that the {@link #tearDownConnection(SlaveComputer, TaskListener)} is in progress.
     * It is used in {@link #afterDisconnect(SlaveComputer, TaskListener)} to avoid multiple parallel calls.
     */
    private transient volatile boolean tearingDownConnection;

    /**
     * Field prefixStartSlaveCmd.
     */
    public String prefixStartSlaveCmd;

    /**
     *  Field suffixStartSlaveCmd.
     */
    public String suffixStartSlaveCmd;

    /**
     *  Field launchTimeoutSeconds.
     */
    public Integer launchTimeoutSeconds;

    /**
     * Field maxNumRetries.
     */
    public Integer maxNumRetries;

    /**
     * Field retryWaitTime (seconds).
     */
    public Integer retryWaitTime;

    /**
     * The verifier to use for checking the SSH key presented by the host
     * responding to the connection
     */
    @CheckForNull
    private final SshHostKeyVerificationStrategy sshHostKeyVerificationStrategy;

    /**
     * Allow to enable/disable the TCP_NODELAY flag on the SSH connection.
     */
    private Boolean tcpNoDelay;

    /**
     * Set the value to add to the remoting parameter -workDir
     * @see <a href="https://github.com/jenkinsci/remoting/blob/master/docs/workDir.md#remoting-work-directory">Remoting Work directory</a>
     */
    private String workDir;

    // TODO: It is a bad idea to create a new Executor service for each launcher.
    // Maybe a Remoting thread pool should be used, but it requires the logic rework to Futures
    /**
     * Keeps executor service for the async launch operation.
     */
    @CheckForNull
    private transient volatile ExecutorService launcherExecutorService;

    /**
     * Constructor SSHModularLauncher creates a new SSHModularLauncher instance.
     *
     * @param host       The host to connect to.
     * @param credentialsId The credentials id to connect as.
     * @param sshHostKeyVerificationStrategy The method for verifying the host key provided.
     * @since 2.0
     */
    @DataBoundConstructor
    public SSHModularLauncher(@NonNull String host, @NonNull String credentialsId,
                              @NonNull SshHostKeyVerificationStrategy sshHostKeyVerificationStrategy) {
        this.host = Util.fixEmptyAndTrim(host);
        this.credentialsId = credentialsId;
        this.sshHostKeyVerificationStrategy = sshHostKeyVerificationStrategy;

        this.port = DEFAULT_SSH_PORT;
        this.launchTimeoutSeconds = DEFAULT_LAUNCH_TIMEOUT_SECONDS;
        this.maxNumRetries = DEFAULT_MAX_NUM_RETRIES;
        this.retryWaitTime = DEFAULT_RETRY_WAIT_TIME;
    }

    @Override
    public String getCredentialsId() {
        return credentialsId;
    }

    @Override
    @CheckForNull
    public SshHostKeyVerificationStrategy getSshHostKeyVerificationStrategy() {
        return sshHostKeyVerificationStrategy;
    }

    /**
     *
     * @since 2.0
     */
    @Override
    @NonNull
    public SshHostKeyVerificationStrategy getSshHostKeyVerificationStrategyDefaulted() {
        return sshHostKeyVerificationStrategy != null ? sshHostKeyVerificationStrategy : new NonVerifyingKeyVerificationStrategy();
    }

    @Override
    public StandardUsernameCredentials getCredentials() {
        // only ever want from the system
        // lookup every time so that we always have the latest
        return SSHCredentialsManager.lookupSystemCredentials(this.credentialsId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isLaunchSupported() {
        return true;
    }

    /**
     * Gets the optional JVM Options used to launch the agent JVM.
     * @return The optional JVM Options used to launch the agent JVM.
     */
    @Override
    public String getJvmOptions() {
        return jvmOptions == null ? "" : jvmOptions;
    }

    @CheckForNull
    private static String getWorkingDirectory(@CheckForNull Slave agent) {
        if (agent == null) {
            return null;
        }
        String workingDirectory = agent.getRemoteFS();
        while (workingDirectory.endsWith("/")) {
            workingDirectory = workingDirectory.substring(0, workingDirectory.length() - 1);
        }
        return workingDirectory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void launch(final SlaveComputer computer, final TaskListener listener) throws InterruptedException {
        listener.getLogger().println(logConfiguration());
        checkConfig();
        final SSHProvider connection = new SSHProviderImpl(this, computer, listener);
        final Node node = computer.getNode();
        final String workingDirectory = getWorkingDirectory(computer);
        if (workingDirectory == null) {
            listener.error("Cannot get the working directory for " + computer);
            //return Boolean.FALSE;
        }
        synchronized (this) {
            launcherExecutorService = Executors.newSingleThreadExecutor(new NamingThreadFactory(
                    Executors.defaultThreadFactory(), "SSHModularLauncher.launch for '" + computer.getName() + "' node"));
            Set<Callable<Boolean>> callables = new HashSet();
            callables.add(new Callable<Boolean>() {
                public Boolean call() throws InterruptedException {
                    Boolean rval = Boolean.FALSE;
                    try {
                        connection.openConnection();
                        String java = "java";
                        if (StringUtils.isNotBlank(javaPath)) {
                            java = expandExpression(computer, javaPath);
                        } 
                        connection.copyAgentJar(workingDirectory);
                        connection.startAgent(java, workingDirectory);
                        PluginImpl.register(connection);
                        rval = Boolean.TRUE;
                    } catch (RuntimeException | Error e) {
                        e.printStackTrace(listener.error(Messages.SSHLauncher_UnexpectedError()));
                    } catch (AbortException e) {
                        listener.getLogger().println(e.getMessage());
                    } catch (IOException e) {
                        e.printStackTrace(listener.getLogger());
                    } finally {
                        return rval;
                    }
                }
            });

            final String nodeName = node != null ? node.getNodeName() : "unknown";
            try {
                long time = System.currentTimeMillis();
                List<Future<Boolean>> results;
                final ExecutorService srv = launcherExecutorService;
                if (srv == null) {
                    throw new IllegalStateException("Launcher Executor Service should be always non-null here, because the task allocates and closes service on its own");
                }
                if (this.getLaunchTimeoutMillis() > 0) {
                    results = srv.invokeAll(callables, this.getLaunchTimeoutMillis(), TimeUnit.MILLISECONDS);
                } else {
                    results = srv.invokeAll(callables);
                }
                long duration = System.currentTimeMillis() - time;
                Boolean res;
                try {
                    res = results.get(0).get();
                } catch (CancellationException | ExecutionException e) {
                    res = Boolean.FALSE;
                    //TODO try to improve the message, set timeout to 1 to expose the error.
                    listener.error( e.getClass().getName()+ " - " + e.getMessage());
                    e.printStackTrace(listener.error(e.getClass().getName()));
                }
                if (!res) {
                    System.out.println(Messages.SSHLauncher_LaunchFailedDuration(getTimestamp(),
                            nodeName, host, duration));
                    listener.getLogger().println(getTimestamp() + " Launch failed - cleaning up connection");
                    connection.cleanupConnection();
                } else {
                    System.out.println(Messages.SSHLauncher_LaunchCompletedDuration(getTimestamp(),
                            nodeName, host, duration));
                }
            } catch (InterruptedException e) {
                System.out.println(Messages.SSHLauncher_LaunchFailed(getTimestamp(), nodeName, host));
            } finally {
                ExecutorService srv = launcherExecutorService;
                if (srv != null) {
                    srv.shutdownNow();
                    launcherExecutorService = null;
                }
            }
        }
        if (node != null && getTrackCredentials()) {
            CredentialsProvider.track(node, getCredentials());
        }
    }

    private void checkConfig() throws InterruptedException {
        DescriptorImpl descriptor = (DescriptorImpl) this.getDescriptor();
        String message = "Validate configuration:\n";
        boolean isValid = true;

        String port = String.valueOf(this.port);
        FormValidation validatePort = descriptor.doCheckPort(port);
        FormValidation validateHost = descriptor.doCheckHost(this.host);
        FormValidation validateCredentials = descriptor.doCheckCredentialsId(Jenkins.get(), Jenkins.get(), this.host, port, this.credentialsId);

        if(validatePort.kind == FormValidation.Kind.ERROR){
            isValid = false;
            message += validatePort.getMessage() + "\n";
        }
        if(validateHost.kind == FormValidation.Kind.ERROR){
            isValid = false;
            message += validateHost.getMessage() + "\n";
        }
        if(validateCredentials.kind == FormValidation.Kind.ERROR){
            isValid = false;
            message += validateCredentials.getMessage() + "\n";
        }

        if(!isValid){
            throw new InterruptedException(message);
        }
    }
    
    private String expandExpression(SlaveComputer computer, String expression) {
        return getEnvVars(computer).expand(expression);
    }

    private EnvVars getEnvVars(SlaveComputer computer) {
        final EnvVars global = getEnvVars(Jenkins.getInstance());

        final Node node = computer.getNode();    
        final EnvVars local = node != null ? getEnvVars(node) : null;

        if (global != null) {
            if (local != null) {
                final EnvVars merged = new EnvVars(global);
                merged.overrideAll(local);

                return merged;
            } else {
                return global;
            }
        } else if (local != null) {
            return local;
        } else {
            return new EnvVars();
        }
    }

    private EnvVars getEnvVars(Jenkins h) {
        return getEnvVars(h.getGlobalNodeProperties());
    }

    private EnvVars getEnvVars(Node n) {
        return getEnvVars(n.getNodeProperties());
    }

    private EnvVars getEnvVars(DescribableList<NodeProperty<?>, NodePropertyDescriptor> dl) {
        final EnvironmentVariablesNodeProperty evnp = dl.get(EnvironmentVariablesNodeProperty.class);
        if (evnp == null) {
            return null;
        }

        return evnp.getEnvVars();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterDisconnect(SlaveComputer slaveComputer, final TaskListener listener) {
        /*
        if (connection == null) {
            // Nothing to do here, the connection is not established
            return;
        }*/

        ExecutorService srv = launcherExecutorService;
        if (srv != null) {
            // If the service is still running, shut it down and interrupt the operations if any
            srv.shutdown();
        }

        if (tearingDownConnection) {
            // tear down operation is in progress, do not even try to synchronize the call.
            //TODO: what if reconnect attempts collide? It should not be possible due to locks, but maybe it worth investigation
            LOGGER.log(Level.FINE, "There is already a tear down operation in progress for connection {0}. Skipping "
                                   + "the call" /*, connection*/);
            return;
        }
        tearDownConnection(slaveComputer, listener);
    }

    private synchronized void tearDownConnection(@NonNull SlaveComputer slaveComputer, final @NonNull TaskListener listener) {
        /*
        if (connection != null) {
            tearDownConnectionImpl(slaveComputer, listener);
        }
        */
    }
    
    /**
     * If the SSH connection as a whole is lost, report that information.
     */
    private boolean reportTransportLoss(Connection c, TaskListener listener) {
        Throwable cause = c.getReasonClosedCause();
        if (cause != null) {
            cause.printStackTrace(listener.error("Socket connection to SSH server was lost"));
        }

        return cause != null;
    }

    /**
     * Getter for property 'host'.
     *
     * @return Value for property 'host'.
     */
    @Override
    public String getHost() {
        return host;
    }

    /**
     * Getter for property 'port'.
     *
     * @return Value for property 'port'.
     */
    @Override
    public int getPort() {
        return port;
    }

    /*
    public Connection getConnection() {
        return connection;
    }*/

    @Override
    @NonNull
    public String getPrefixStartSlaveCmd() {
        return Util.fixNull(prefixStartSlaveCmd);
    }

    @Override
    @NonNull
    public String getSuffixStartSlaveCmd() {
        return Util.fixNull(suffixStartSlaveCmd);
    }

    /**
     * Getter for property 'launchTimeoutSeconds'
     *
     * @return launchTimeoutSeconds
     */
    @Override
    @NonNull
    public Integer getLaunchTimeoutSeconds() {
        return launchTimeoutSeconds;
    }

    /**
     *
     * @since 2.0
     */
    public long getLaunchTimeoutMillis() {
        return TimeUnit.SECONDS.toMillis(defaultIfNoValid(launchTimeoutSeconds, DEFAULT_LAUNCH_TIMEOUT_SECONDS));
    }

    /**
     * Getter for property 'maxNumRetries'
     *
     * @return maxNumRetries
     */
    @Override
    @NonNull
    public Integer getMaxNumRetries() {
        return defaultIfNoValid(maxNumRetries, DEFAULT_MAX_NUM_RETRIES);
    }

    /**
     * Getter for property 'retryWaitTime'
     *
     * @return retryWaitTime
     */
    @Override
    @NonNull
    public Integer getRetryWaitTime() {
        return defaultIfNoValid(retryWaitTime, DEFAULT_RETRY_WAIT_TIME);
    }

    @Override
    public boolean getTcpNoDelay() {
        return tcpNoDelay != null ? tcpNoDelay : true;
    }

    /**
     * Enable/Disable the credential tracking, this tracking store information about where it is used a credential,
     * in this case in a node. If the tracking is enabled and you launch a big number of Agents per day, activate
     * credentials tacking could cause a performance issue see
     * @see  <a href="https://issues.jenkins-ci.org/browse/JENKINS-49235">JENKINS-49235</a>
     */
    @Override
    public boolean getTrackCredentials() {
        String trackCredentials = System.getProperty(SSHModularLauncher.class.getName() + ".trackCredentials");
        return !"false".equalsIgnoreCase(trackCredentials);
    }

    @Override
    public String getWorkDir() {
        return workDir;
    }

    @Override
    public String getJavaPath() {
        return javaPath;
    }

    @DataBoundSetter
    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    @DataBoundSetter
    public void setWorkDir(String workDir) {
        this.workDir = Util.fixEmptyAndTrim(workDir);
    }

    @DataBoundSetter
    public void setPort(int port) {
        this.port = port == 0 ? DEFAULT_SSH_PORT : port;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = Util.fixEmpty(credentialsId);
    }

    @DataBoundSetter
    public void setJvmOptions(String jvmOptions) {
        this.jvmOptions = Util.fixEmpty(jvmOptions);
    }

    @DataBoundSetter
    public void setJavaPath(String javaPath) {
        this.javaPath = fixEmpty(javaPath);
    }

    @DataBoundSetter
    public void setPrefixStartSlaveCmd(String prefixStartSlaveCmd) {
        this.prefixStartSlaveCmd = fixEmpty(prefixStartSlaveCmd);
    }

    @DataBoundSetter
    public void setSuffixStartSlaveCmd(String suffixStartSlaveCmd) {
        this.suffixStartSlaveCmd = fixEmpty(suffixStartSlaveCmd);
    }

    @DataBoundSetter
    public void setLaunchTimeoutSeconds(Integer launchTimeoutSeconds) {
        this.launchTimeoutSeconds = defaultIfNoValid(launchTimeoutSeconds, DEFAULT_LAUNCH_TIMEOUT_SECONDS);
    }

    @DataBoundSetter
    public void setMaxNumRetries(Integer maxNumRetries) {
        this.maxNumRetries = defaultIfNoValid(maxNumRetries, DEFAULT_MAX_NUM_RETRIES);
    }

    @DataBoundSetter
    public void setRetryWaitTime(Integer retryWaitTime) {
        this.retryWaitTime = defaultIfNoValid(retryWaitTime, DEFAULT_RETRY_WAIT_TIME);
    }

    /**
     *
     * @param value value to evaluate.
     * @param defaultValue default value.
     * @return return the value if it is not null and >0, otherwise return the default value.
     */
    private Integer defaultIfNoValid(Integer value, Integer defaultValue){
        return value != null && value > 0 ? value : defaultValue;
    }

    /**
     * Returns the remote root workspace (without trailing slash).
     *
     * @param computer The slave computer to get the root workspace of.
     *
     * @return the remote root workspace (without trailing slash).
     *
     */
    @CheckForNull
    static String getWorkingDirectory(SlaveComputer computer) {
        return SSHModularLauncher.getWorkingDirectory(computer.getNode());
    }

    /**
     * @param workingDirectory The Working directory set on the configuration of the node.
     * @return the remoting parameter to set the workDir,
     * by default it is the same as the working directory configured on the node so "-workDir " + workingDirectory,
     * if workDir is set, he method will return "-workDir " + getWorkDir()
     * if the parameter is set in suffixStartSlaveCmd, the method will return an empty String.
     */
    @NonNull
    @Restricted(NoExternalUse.class)
    public String getWorkDirParam(@NonNull String workingDirectory){
        String ret;
        if(getSuffixStartSlaveCmd().contains(WORK_DIR_PARAM)){
            //the parameter is already set on suffixStartSlaveCmd
            ret = "";
        } else if (StringUtils.isNotBlank(getWorkDir())){
            ret = WORK_DIR_PARAM + getWorkDir();
        } else {
            ret = WORK_DIR_PARAM + workingDirectory;
        }
        return ret;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ComputerLauncher> {

        /**
         * {@inheritDoc}
         */
        public String getDisplayName() {
            return Messages.SSHLauncher_DescriptorDisplayName() + "(experimental)";
        }

        public Class getSshConnectorClass() {
            return SSHConnector.class;
        }

        /**
         * Delegates the help link to the {@link SSHConnector}.
         */
        @Override
        public String getHelpFile(String fieldName) {
            String n = super.getHelpFile(fieldName);
            if (n==null)
                n = Jenkins.getInstance().getDescriptorOrDie(SSHConnector.class).getHelpFile(fieldName);
            return n;
        }

        @RequirePOST
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath AccessControlled context,
                                                     @QueryParameter String host,
                                                     @QueryParameter String port,
                                                     @QueryParameter String credentialsId) {
            try {
                int portValue = Integer.parseInt(port);
                return SSHCredentialsManager.fillCredentialsIdItems(context, host, portValue, credentialsId);
            } catch (NumberFormatException ex) {
                return new StandardUsernameListBoxModel().includeCurrentValue(credentialsId);
            }
        }

        @RequirePOST
        public FormValidation doCheckCredentialsId(@AncestorInPath ItemGroup context,
                                                   @AncestorInPath AccessControlled _context,
                                                   @QueryParameter String host,
                                                   @QueryParameter String port,
                                                   @QueryParameter String value) {
            /** TODO check this method with the original */
            try {
                int portValue = Integer.parseInt(port);
                return SSHCredentialsManager.checkCredentialsIdAndDomain(_context, host, portValue, value);
            } catch (NumberFormatException e) {
                return FormValidation.warning(e, Messages.SSHLauncher_PortNotANumber());
            }
        }

        public FormValidation doCheckPort(@QueryParameter String value) {
            if (StringUtils.isEmpty(value)) {
                return FormValidation.error(Messages.SSHLauncher_PortNotSpecified());
            }
            try {
                int portValue = Integer.parseInt(value);
                if (portValue <= 0) {
                    return FormValidation.error(Messages.SSHLauncher_PortLessThanZero());
                }
                if (portValue >= 65536) {
                    return FormValidation.error(Messages.SSHLauncher_PortMoreThan65535());
                }
                return FormValidation.ok();
            } catch (NumberFormatException e) {
                return FormValidation.error(e, Messages.SSHLauncher_PortNotANumber());
            }
        }
        
        public FormValidation doCheckHost(@QueryParameter String value) {
            FormValidation ret = FormValidation.ok();
            if (StringUtils.isEmpty(value)) {
                return FormValidation.error(Messages.SSHLauncher_HostNotSpecified());
            }
            return ret;
        }
    }

    public String logConfiguration() {
        final StringBuilder sb = new StringBuilder("SSHModularLauncher{");
        sb.append("host='").append(getHost()).append('\'');
        sb.append(", port=").append(getPort());
        sb.append(", credentialsId='").append(Util.fixNull(credentialsId)).append('\'');
        sb.append(", jvmOptions='").append(getJvmOptions()).append('\'');
        sb.append(", javaPath='").append(Util.fixNull(javaPath)).append('\'');
        sb.append(", prefixStartSlaveCmd='").append(getPrefixStartSlaveCmd()).append('\'');
        sb.append(", suffixStartSlaveCmd='").append(getSuffixStartSlaveCmd()).append('\'');
        sb.append(", launchTimeoutSeconds=").append(getLaunchTimeoutSeconds());
        sb.append(", maxNumRetries=").append(getMaxNumRetries());
        sb.append(", retryWaitTime=").append(getRetryWaitTime());
        sb.append(", sshHostKeyVerificationStrategy=").append(sshHostKeyVerificationStrategy != null ?
                                                              sshHostKeyVerificationStrategy.getClass().getName() : "None");
        sb.append(", tcpNoDelay=").append(getTcpNoDelay());
        sb.append(", trackCredentials=").append(getTrackCredentials());
        sb.append('}');
        return sb.toString();
    }
}
