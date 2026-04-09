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
package hudson.plugins.sshslaves.mina;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.HostnamePortRequirement;
import com.cloudbees.plugins.credentials.domains.SchemeRequirement;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.Extension;
import hudson.Functions;
import hudson.Util;
import hudson.init.Terminator;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ModelObject;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import net.jcip.annotations.GuardedBy;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.AttributeRepository;
import org.apache.sshd.common.AttributeStore;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.scp.client.DefaultScpClient;
import org.apache.sshd.scp.client.ScpClient;
import org.apache.sshd.scp.common.ScpTransferEventListener;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.apache.sshd.sftp.common.SftpException;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

/**
 * SSH launcher that uses Apache Mina SSHD for connections.
 *
 * <p>This is an alternative to the Trilead-based {@link hudson.plugins.sshslaves.SSHLauncher},
 * providing NIO-based SSH connections with modern cryptography support via Apache Mina SSHD.
 *
 * <p>A shared {@link SshClient} instance (managed by {@link MinaSshClient}) is reused across
 * all connections for resource efficiency.
 */
public class MinaSSHLauncher extends ComputerLauncher {

    private static final Logger LOGGER = Logger.getLogger(MinaSSHLauncher.class.getName());

    public static final int DEFAULT_SSH_PORT = 22;
    public static final Integer DEFAULT_MAX_NUM_RETRIES = 10;
    public static final Integer DEFAULT_RETRY_WAIT_TIME = 15;
    public static final Integer DEFAULT_LAUNCH_TIMEOUT_SECONDS = 60;
    public static final String AGENT_JAR = "remoting.jar";
    public static final SchemeRequirement SSH_SCHEME = new SchemeRequirement("ssh");

    /**
     * Default timeout for SSH operations in milliseconds.
     */
    public static final int TIMEOUT = Integer.getInteger(MinaSSHLauncher.class.getName() + ".TIMEOUT", 60000);

    @GuardedBy("class")
    private static WeakReference<byte[]> agentJarBytes;

    private final String host;
    private final int port;
    private final String credentialsId;

    private String jvmOptions;
    private String javaPath;
    private String prefixStartSlaveCmd;
    private String suffixStartSlaveCmd;
    private Integer launchTimeoutSeconds;
    private Integer maxNumRetries;
    private Integer retryWaitTime;
    private Boolean tcpNoDelay;
    private String workDir;
    private MinaServerKeyVerificationStrategy serverKeyVerificationStrategy;

    private transient volatile ClientSession session;

    @DataBoundConstructor
    public MinaSSHLauncher(@NonNull String host, int port, String credentialsId) {
        this.host = host;
        this.port = port == 0 ? DEFAULT_SSH_PORT : port;
        this.credentialsId = credentialsId;
    }

    @NonNull
    public String getHost() {
        return host;
    }

    public int getPort() {
        return port == 0 ? DEFAULT_SSH_PORT : port;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getJvmOptions() {
        return jvmOptions;
    }

    @DataBoundSetter
    public void setJvmOptions(String jvmOptions) {
        this.jvmOptions = Util.fixEmpty(jvmOptions);
    }

    public String getJavaPath() {
        return javaPath;
    }

    @DataBoundSetter
    public void setJavaPath(String javaPath) {
        this.javaPath = Util.fixEmpty(javaPath);
    }

    public String getPrefixStartSlaveCmd() {
        return prefixStartSlaveCmd;
    }

    @DataBoundSetter
    public void setPrefixStartSlaveCmd(String prefixStartSlaveCmd) {
        this.prefixStartSlaveCmd = Util.fixEmpty(prefixStartSlaveCmd);
    }

    public String getSuffixStartSlaveCmd() {
        return suffixStartSlaveCmd;
    }

    @DataBoundSetter
    public void setSuffixStartSlaveCmd(String suffixStartSlaveCmd) {
        this.suffixStartSlaveCmd = Util.fixEmpty(suffixStartSlaveCmd);
    }

    public Integer getLaunchTimeoutSeconds() {
        return launchTimeoutSeconds == null ? DEFAULT_LAUNCH_TIMEOUT_SECONDS : launchTimeoutSeconds;
    }

    @DataBoundSetter
    public void setLaunchTimeoutSeconds(Integer launchTimeoutSeconds) {
        this.launchTimeoutSeconds = launchTimeoutSeconds;
    }

    public Integer getMaxNumRetries() {
        return maxNumRetries == null ? DEFAULT_MAX_NUM_RETRIES : maxNumRetries;
    }

    @DataBoundSetter
    public void setMaxNumRetries(Integer maxNumRetries) {
        this.maxNumRetries = maxNumRetries;
    }

    public Integer getRetryWaitTime() {
        return retryWaitTime == null ? DEFAULT_RETRY_WAIT_TIME : retryWaitTime;
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
        this.workDir = Util.fixEmpty(workDir);
    }

    public MinaServerKeyVerificationStrategy getServerKeyVerificationStrategy() {
        return serverKeyVerificationStrategy;
    }

    @DataBoundSetter
    public void setServerKeyVerificationStrategy(MinaServerKeyVerificationStrategy serverKeyVerificationStrategy) {
        this.serverKeyVerificationStrategy = serverKeyVerificationStrategy;
    }

    @Override
    public boolean isLaunchSupported() {
        return true;
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
        long startTime = System.currentTimeMillis();

        StandardUsernameCredentials credentials = lookupCredentials();
        if (credentials == null) {
            throw new AbortException("[SSH Mina] Cannot find specified credentials: " + credentialsId);
        }
        if (!SSHAuthenticator.isSupported(ClientSession.class, credentials.getClass())) {
            throw new AbortException(
                    "[SSH Mina] Incompatible credentials: " + CredentialsNameProvider.name(credentials));
        }

        MinaServerKeyVerificationStrategy strategy = getServerKeyVerificationStrategyDefaulted();
        List<String> preferredAlgorithms = strategy.getPreferredKeyAlgorithms();

        ClientSession connectedSession = openConnection(listener, computer, credentials, preferredAlgorithms);
        this.session = connectedSession;

        // Set up key verification
        connectedSession
                .getMetadataMap()
                .put(
                        ServerKeyVerifier.class,
                        new LoggingServerKeyVerifier(strategy.createVerifier(computer, host), listener));

        // Authenticate
        println(listener, "Authenticating as " + CredentialsNameProvider.name(credentials));
        if (!SSHAuthenticator.newInstance(connectedSession, credentials).authenticate(listener)) {
            println(listener, "Authentication failed");
            connectedSession.close(true);
            return;
        }
        println(listener, "Authentication successful");

        try {
            verifyNoHeaderJunk(connectedSession, listener);
            String workingDirectory = getWorkingDirectory(computer);
            println(listener, "Remote SSH server: " + connectedSession.getServerVersion());

            String java = resolveJava(connectedSession, listener);
            copyAgentJar(connectedSession, listener, workingDirectory);
            startAgent(computer, listener, connectedSession, workingDirectory, java);

            double elapsed = (System.currentTimeMillis() - startTime) * 1e-3;
            println(listener, "Connection established after " + String.format("%.1f", elapsed) + " seconds");
        } catch (RuntimeException e) {
            connectedSession.close(true);
            throw e;
        } catch (Throwable e) {
            connectedSession.close(true);
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    public void afterDisconnect(SlaveComputer slaveComputer, TaskListener listener) {
        ClientSession currentSession = this.session;
        if (currentSession != null) {
            try {
                currentSession.close(true);
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Error closing Mina SSH session", e);
            }
            this.session = null;
        }
        super.afterDisconnect(slaveComputer, listener);
    }

    private ClientSession openConnection(
            TaskListener listener,
            SlaveComputer computer,
            StandardUsernameCredentials credentials,
            List<String> preferredAlgorithms)
            throws IOException, InterruptedException {
        SshClient client = MinaSshClient.getClient();
        int effectivePort = getPort();
        int maxRetries = getMaxNumRetries();
        int retryWait = getRetryWaitTime();

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                println(
                        listener,
                        "Opening SSH connection to "
                                + host + ":" + effectivePort
                                + " as " + CredentialsNameProvider.name(credentials));

                // Create connection context with preferred algorithms if specified
                AttributeStore context = null;
                if (!preferredAlgorithms.isEmpty()) {
                    context = createAttributeStore();
                    context.setAttribute(MinaSshClient.PREFERRED_ALGORITHMS_KEY, preferredAlgorithms);
                }

                ConnectFuture future = client.connect(credentials.getUsername(), host, effectivePort, context, null);
                future.await(getLaunchTimeoutSeconds(), TimeUnit.SECONDS);

                if (!future.isConnected()) {
                    throw new IOException("SSH connection timed out");
                }

                return future.getSession();
            } catch (IOException e) {
                if (attempt < maxRetries) {
                    println(
                            listener,
                            "SSH connection failed: " + e.getMessage() + ". Retrying in " + retryWait + " seconds ("
                                    + (attempt + 1) + "/" + maxRetries + ")");
                    Thread.sleep(retryWait * 1000L);
                } else {
                    throw e;
                }
            }
        }
        throw new IOException("SSH connection failed after " + maxRetries + " retries");
    }

    private void startAgent(
            SlaveComputer computer,
            TaskListener listener,
            ClientSession connectedSession,
            String workingDirectory,
            String java)
            throws IOException, InterruptedException {
        String prefix = StringUtils.isNotBlank(prefixStartSlaveCmd) ? prefixStartSlaveCmd + " " : "";
        String suffix = StringUtils.isNotBlank(suffixStartSlaveCmd) ? " " + suffixStartSlaveCmd : "";
        String jvmOpts = StringUtils.defaultString(jvmOptions);

        String cmd = prefix + "cd \"" + workingDirectory + "\" && \"" + java + "\" " + jvmOpts + " -jar " + AGENT_JAR
                + suffix;

        ChannelExec process = connectedSession.createExecChannel(cmd);
        println(listener, "$ " + cmd);
        process.open().await();

        // Pipe stderr to listener for diagnostics
        process.setErr(CloseShieldOutputStream.wrap(listener.getLogger()));

        computer.setChannel(
                process.getInvertedOut(), process.getInvertedIn(), listener.getLogger(), new Channel.Listener() {
                    @Override
                    public void onClosed(Channel channel, IOException cause) {
                        if (cause != null) {
                            Functions.printStackTrace(cause, listener.error("[SSH Mina] Agent terminated"));
                        }
                        try {
                            process.close(true);
                        } catch (Throwable t) {
                            Functions.printStackTrace(t, listener.error("[SSH Mina] Error while closing SSH channel"));
                        }
                        try {
                            connectedSession.close(true);
                        } catch (Throwable t) {
                            Functions.printStackTrace(t, listener.error("[SSH Mina] Error while closing SSH session"));
                        }
                    }
                });
    }

    private void copyAgentJar(ClientSession connectedSession, TaskListener listener, String workingDirectory)
            throws IOException {
        String fileName =
                workingDirectory.endsWith("/") ? workingDirectory + AGENT_JAR : workingDirectory + "/" + AGENT_JAR;
        byte[] slaveJar = getAgentJarBytes();

        // Check if jar is already current (MD5 check)
        String digest = Util.getDigestOf(new ByteArrayInputStream(slaveJar));
        println(listener, "Verifying agent jar...");
        ChannelExec execChannel = null;
        try {
            String cmd = "mkdir -p \"" + workingDirectory + "\" 2>/dev/null ; md5sum \"" + fileName + "\" || md5 \""
                    + fileName + "\"";
            execChannel = connectedSession.createExecChannel(cmd);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            execChannel.setOut(baos);
            execChannel.setErr(baos);
            execChannel.open().await();
            execChannel.waitFor(Arrays.asList(ClientChannelEvent.EOF, ClientChannelEvent.EXIT_STATUS), TIMEOUT);
            execChannel.close(false);
            execChannel = null;
            String output = baos.toString(StandardCharsets.UTF_8);
            if (output.toLowerCase().contains(digest) && digest.length() > 30) {
                println(listener, "Agent jar is current (" + digest + ")");
                return;
            }
        } catch (IOException e) {
            // fall through to copy
        } finally {
            if (execChannel != null) {
                execChannel.close(true);
            }
        }

        // Try SFTP first
        SftpClientFactory factory = SftpClientFactory.instance();
        try (SftpClient sftp = factory.createSftpClient(connectedSession)) {
            println(listener, "Copying agent jar via SFTP...");
            try {
                SftpClient.Attributes stat = sftp.stat(workingDirectory);
                if (stat.isRegularFile()) {
                    throw new IOException("Remote FS " + workingDirectory + " is a file, not a directory");
                }
            } catch (SshException | SftpException e) {
                println(listener, "Remote directory " + workingDirectory + " does not exist, creating...");
                mkdirs(sftp, workingDirectory, 0700);
            }
            try {
                sftp.remove(fileName);
            } catch (IOException e) {
                // file did not exist
            }
            try (SftpClient.CloseableHandle handle =
                    sftp.open(fileName, EnumSet.of(SftpClient.OpenMode.Create, SftpClient.OpenMode.Write))) {
                int offset = 0;
                while (offset < slaveJar.length) {
                    int size = Math.min(32768, slaveJar.length - offset);
                    sftp.write(handle, offset, slaveJar, offset, size);
                    offset += size;
                }
                println(listener, "Copied " + offset + " bytes via SFTP");
            } catch (Exception e) {
                connectedSession.close(true);
                throw new IOException("Error copying agent jar into " + workingDirectory, e);
            }
        } catch (IOException e) {
            // SFTP failed, try SCP
            println(listener, "SFTP failed, trying SCP...");
            ScpClient client = new DefaultScpClient(connectedSession, null, new ScpTransferEventListener() {
                @Override
                public void startFileEvent(
                        Session session, FileOperation op, Path path, long length, Set<PosixFilePermission> perms) {
                    println(listener, "Sending file " + path);
                }

                @Override
                public void endFileEvent(
                        Session session,
                        FileOperation op,
                        Path path,
                        long length,
                        Set<PosixFilePermission> perms,
                        Throwable thrown) {
                    println(listener, "Sent file " + path);
                }

                @Override
                public void startFolderEvent(
                        Session session, FileOperation op, Path path, Set<PosixFilePermission> perms) {}

                @Override
                public void endFolderEvent(
                        Session session,
                        FileOperation op,
                        Path path,
                        Set<PosixFilePermission> perms,
                        Throwable thrown) {}
            });
            client.upload(slaveJar, fileName, Collections.singleton(PosixFilePermission.OWNER_READ), null);
            println(listener, "Copied " + slaveJar.length + " bytes via SCP");
        }
    }

    private void verifyNoHeaderJunk(ClientSession connectedSession, TaskListener listener)
            throws IOException, InterruptedException {
        println(listener, "Checking for header junk...");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ChannelExec execChannel = connectedSession.createExecChannel("true");
        try {
            execChannel.setOut(baos);
            execChannel.open().await();
            execChannel.waitFor(Collections.singleton(ClientChannelEvent.EOF), TIMEOUT);
        } finally {
            execChannel.close(false);
        }
        if (baos.toByteArray().length != 0) {
            String junk = baos.toString(StandardCharsets.UTF_8);
            println(listener, "Header junk detected: " + junk);
            throw new AbortException(
                    "SSH header junk detected. Please disable banner printing and .profile/.bashrc output.");
        }
        println(listener, "No header junk");
    }

    private String resolveJava(ClientSession connectedSession, TaskListener listener)
            throws IOException, InterruptedException {
        println(listener, "Verifying Java...");
        if (StringUtils.isNotBlank(javaPath)) {
            return javaPath;
        }

        List<String> candidates = Arrays.asList(
                "java",
                "/usr/bin/java",
                "/usr/java/default/bin/java",
                "/usr/java/latest/bin/java",
                "/usr/local/bin/java",
                "/usr/local/java/bin/java");
        List<String> tried = new ArrayList<>();

        for (String javaCommand : candidates) {
            println(listener, "Trying " + javaCommand + "...");
            tried.add(javaCommand);
            ChannelExec execChannel = null;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                String cmd = "\"" + javaCommand + "\" " + StringUtils.defaultString(jvmOptions) + " -version 2>&1";
                execChannel = connectedSession.createExecChannel(cmd);
                execChannel.setOut(baos);
                execChannel.setErr(baos);
                execChannel.open().await();
                execChannel.waitFor(Collections.singletonList(ClientChannelEvent.EOF), TIMEOUT);
                execChannel.close(false);
                execChannel = null;

                String output = baos.toString(StandardCharsets.UTF_8);
                // Check for a valid Java version output
                checkJavaVersion(listener, javaCommand, output);
                return javaCommand;
            } catch (IOException e) {
                // try next
            } finally {
                if (execChannel != null) {
                    execChannel.close(true);
                }
            }
        }
        throw new IOException("Could not find any known supported Java version in " + tried);
    }

    private void checkJavaVersion(TaskListener listener, String javaCommand, String output) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.contains("version")) {
                println(listener, "Found " + javaCommand + ": " + line.trim());
                return;
            }
        }
        throw new IOException(javaCommand + " -version did not produce recognizable output: " + output);
    }

    private MinaServerKeyVerificationStrategy getServerKeyVerificationStrategyDefaulted() {
        if (serverKeyVerificationStrategy == null) {
            return new BlindTrustVerificationStrategy();
        }
        return serverKeyVerificationStrategy;
    }

    private StandardUsernameCredentials lookupCredentials() {
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentialsInItemGroup(
                        StandardUsernameCredentials.class, Jenkins.get(), ACL.SYSTEM2, List.of(SSH_SCHEME)),
                CredentialsMatchers.withId(credentialsId));
    }

    private static String getWorkingDirectory(SlaveComputer computer) {
        Slave node = computer.getNode();
        if (node == null) {
            throw new IllegalStateException("Node is null");
        }
        String dir = node.getRemoteFS();
        while (dir.endsWith("/")) {
            dir = dir.substring(0, dir.length() - 1);
        }
        return dir;
    }

    private static synchronized byte[] getAgentJarBytes() throws IOException {
        byte[] ref = agentJarBytes == null ? null : agentJarBytes.get();
        if (ref != null) {
            return ref;
        }
        ref = new Slave.JnlpJar(AGENT_JAR).readFully();
        agentJarBytes = new WeakReference<>(ref);
        return ref;
    }

    private static void mkdirs(SftpClient sftp, String path, int mode) throws IOException {
        if (!path.endsWith("/")) {
            path = path + "/";
        }
        for (int i = path.indexOf('/'); i != -1; i = path.indexOf('/', i + 1)) {
            if (i == 0) {
                continue;
            }
            try {
                sftp.stat(path.substring(0, i));
            } catch (SftpException e) {
                sftp.mkdir(path.substring(0, i));
                sftp.setStat(path.substring(0, i), new SftpClient.Attributes().perms(mode));
            }
        }
    }

    private static AttributeStore createAttributeStore() {
        return new AttributeStore() {
            private final ConcurrentHashMap<AttributeRepository.AttributeKey<?>, Object> attributes =
                    new ConcurrentHashMap<>();

            @Override
            @SuppressWarnings("unchecked")
            public <T> T getAttribute(AttributeRepository.AttributeKey<T> key) {
                return (T) attributes.get(key);
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T setAttribute(AttributeRepository.AttributeKey<T> key, T value) {
                return value != null ? (T) attributes.put(key, value) : (T) attributes.remove(key);
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T removeAttribute(AttributeRepository.AttributeKey<T> key) {
                return (T) attributes.remove(key);
            }

            @Override
            public int getAttributesCount() {
                return attributes.size();
            }

            @Override
            public void clearAttributes() {
                attributes.clear();
            }

            @Override
            public Collection<AttributeKey<?>> attributeKeys() {
                return attributes.keySet();
            }
        };
    }

    /**
     * Shuts down the shared Mina SSH client when Jenkins is stopping.
     */
    @Terminator
    public static void stopMinaSshClient() {
        MinaSshClient.stop();
    }

    private static void println(TaskListener listener, String message) {
        listener.getLogger().println("[SSH Mina] " + message);
    }

    @Extension
    @Symbol("sshMina")
    public static class DescriptorImpl extends Descriptor<ComputerLauncher> {

        @Override
        @NonNull
        public String getDisplayName() {
            return Messages.MinaSSHLauncher_DisplayName();
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillCredentialsIdItems(
                @AncestorInPath ModelObject context,
                @QueryParameter String host,
                @QueryParameter String port,
                @QueryParameter String credentialsId) {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            AccessControlled aclHolder = context instanceof AccessControlled ? (AccessControlled) context : jenkins;
            if (aclHolder == null) {
                return new StandardUsernameListBoxModel();
            }
            if (aclHolder instanceof Item) {
                if (!aclHolder.hasPermission(Item.CONFIGURE)) {
                    aclHolder.checkPermission(Item.EXTENDED_READ);
                    return new StandardUsernameListBoxModel();
                }
            } else if (aclHolder instanceof Computer || aclHolder == jenkins) {
                if (!aclHolder.hasPermission(Computer.CONFIGURE)) {
                    aclHolder.checkPermission(Computer.EXTENDED_READ);
                    return new StandardUsernameListBoxModel();
                }
            } else {
                return new StandardUsernameListBoxModel();
            }

            List<DomainRequirement> domainRequirements = new ArrayList<>();
            domainRequirements.add(SSH_SCHEME);
            if (StringUtils.isNotBlank(host)) {
                try {
                    int portValue = StringUtils.isBlank(port) ? DEFAULT_SSH_PORT : Integer.parseInt(port);
                    domainRequirements.add(new HostnamePortRequirement(host, portValue));
                } catch (NumberFormatException e) {
                    // ignore invalid port
                }
            }

            if (context instanceof Item) {
                return new StandardUsernameListBoxModel()
                        .includeMatchingAs(
                                ACL.SYSTEM,
                                (Item) context,
                                StandardUsernameCredentials.class,
                                domainRequirements,
                                SSHAuthenticator.matcher(ClientSession.class));
            } else {
                return new StandardUsernameListBoxModel()
                        .includeMatchingAs(
                                ACL.SYSTEM,
                                jenkins,
                                StandardUsernameCredentials.class,
                                domainRequirements,
                                SSHAuthenticator.matcher(ClientSession.class));
            }
        }

        @POST
        @SuppressWarnings("unused")
        public FormValidation doCheckHost(@QueryParameter String value) {
            if (StringUtils.isBlank(value)) {
                return FormValidation.error("Host is required");
            }
            return FormValidation.ok();
        }

        @POST
        @SuppressWarnings("unused")
        public FormValidation doCheckPort(@QueryParameter String value) {
            if (StringUtils.isNotBlank(value)) {
                try {
                    int port = Integer.parseInt(value);
                    if (port < 1 || port > 65535) {
                        return FormValidation.error("Port must be between 1 and 65535");
                    }
                } catch (NumberFormatException e) {
                    return FormValidation.error("Invalid port number");
                }
            }
            return FormValidation.ok();
        }

        @POST
        @SuppressWarnings("unused")
        public FormValidation doCheckCredentialsId(@QueryParameter String value) {
            if (StringUtils.isBlank(value)) {
                return FormValidation.error("Credentials are required");
            }
            return FormValidation.ok();
        }
    }
}
