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
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUser;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.HostnamePortRequirement;
import com.cloudbees.plugins.credentials.domains.SchemeRequirement;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.trilead.ssh2.ChannelCondition;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.SCPClient;
import com.trilead.ssh2.SFTPv3Client;
import com.trilead.ssh2.SFTPv3FileAttributes;
import com.trilead.ssh2.Session;
import com.trilead.ssh2.transport.TransportManager;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.ItemGroup;
import hudson.model.JDK;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.SlaveComputer;
import hudson.tools.JDKInstaller;
import hudson.tools.JDKInstaller.CPU;
import hudson.tools.JDKInstaller.Platform;
import hudson.tools.ToolLocationNodeProperty;
import hudson.tools.ToolLocationNodeProperty.ToolLocation;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import hudson.util.IOException2;
import hudson.util.ListBoxModel;
import hudson.util.NamingThreadFactory;
import hudson.util.NullStream;
import hudson.util.Secret;
import java.util.Collections;
import jenkins.model.Jenkins;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.TeeOutputStream;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.putty.PuTTYKey;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.lang.InterruptedException;
import java.lang.reflect.Field;
import java.net.URL;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static com.cloudbees.plugins.credentials.CredentialsMatchers.*;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import static hudson.Util.*;
import hudson.model.Computer;
import hudson.security.AccessControlled;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import static java.util.logging.Level.*;

/**
 * A computer launcher that tries to start a linux slave by opening an SSH connection and trying to find java.
 */
public class SSHLauncher extends ComputerLauncher {

    /**
     * The scheme requirement.
     */
    public static final SchemeRequirement SSH_SCHEME = new SchemeRequirement("ssh");


    public static final String JDKVERSION = "jdk-8u181";
    public static final String DEFAULT_JDK = JDKVERSION + "-oth-JPR";

    // Some of the messages observed in the wild:
    // "Connection refused (Connection refused)"
    // "Connection reset"
    // "Connection timed out", "Connection timed out (Connection timed out)"
    // "No route to host", "No route to host (Host unreachable)"
    // "Premature connection close"
    private static final List<String> RECOVERABLE_FAILURES = Arrays.asList(
            "Connection refused", "Connection reset", "Connection timed out", "No route to host", "Premature connection close"
    );

    /**
     * @deprecated
     *      Subtype of {@link JDKInstaller} causes JENKINS-10641.
     */
    public static class DefaultJDKInstaller extends JDKInstaller {
        public DefaultJDKInstaller() {
            super(DEFAULT_JDK, true);
        }

        public Object readResolve() {
            return new JDKInstaller(DEFAULT_JDK,true);
        }
    }

    /**
     * Field host
     */
    private final String host;

    /**
     * Field port
     */
    private final int port;

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
     * @deprecated
     */
    @Deprecated
    private transient String username;

    /**
     * Field password
     * @deprecated
     */
    @Deprecated
    private transient Secret password;

    /**
     * File path of the private key.
     * @deprecated
     */
    @Deprecated
    private transient String privatekey;

    /**
     * Field jvmOptions.
     */
    private final String jvmOptions;

    /**
     * Field javaPath.
     */
    public final String javaPath;

    /**
     * to install JDK, keep this field null. This avoids baking the default value into the persisted form.
     * @see #getJDKInstaller()
     */
    private JDKInstaller jdk = null;

    /**
     * SSH connection to the slave.
     */
    private transient Connection connection;

    /**
     * The session inside {@link #connection} that controls the slave process.
     */
    private transient Session session;

    /**
     * Field prefixStartSlaveCmd.
     */
    public final String prefixStartSlaveCmd;

    /**
     *  Field suffixStartSlaveCmd.
     */
    public final String suffixStartSlaveCmd;

    /**
     *  Field launchTimeoutSeconds.
     */
    public final Integer launchTimeoutSeconds;

    /**
     * Field maxNumRetries.
     */
    public final Integer maxNumRetries;

    /**
     * Field retryWaitTime.
     */
    public final Integer retryWaitTime;

    /**
     * Constructor SSHLauncher creates a new SSHLauncher instance.
     *
     * @param host       The host to connect to.
     * @param port       The port to connect on.
     * @param credentialsId The credentials id to connect as.
     * @param jvmOptions Options passed to the java vm.
     * @param javaPath   Path to the host jdk installation. If <code>null</code> the jdk will be auto detected or installed by the JDKInstaller.
     * @param prefixStartSlaveCmd This will prefix the start slave command. For instance if you want to execute the command with a different shell.
     * @param suffixStartSlaveCmd This will suffix the start slave command.
     * @param launchTimeoutSeconds Launch timeout in seconds
     * @param maxNumRetries The number of times to retry connection if the SSH connection is refused during initial connect
     * @param retryWaitTime The number of seconds to wait between retries
     */
    @DataBoundConstructor
    public SSHLauncher(String host, int port, String credentialsId,
             String jvmOptions, String javaPath, String prefixStartSlaveCmd, String suffixStartSlaveCmd,
             Integer launchTimeoutSeconds, Integer maxNumRetries, Integer retryWaitTime) {
        this(host, port, lookupSystemCredentials(credentialsId), jvmOptions, javaPath, null, prefixStartSlaveCmd,
             suffixStartSlaveCmd, launchTimeoutSeconds, maxNumRetries, retryWaitTime);
    }

    /** @deprecated Use {@link #SSHLauncher(String, int, String, String, String, String, String, Integer, Integer, Integer)} instead. */
    @Deprecated
    public SSHLauncher(String host, int port, String credentialsId,
                       String jvmOptions, String javaPath, String prefixStartSlaveCmd, String suffixStartSlaveCmd,
                       Integer launchTimeoutSeconds) {
        this(host, port, lookupSystemCredentials(credentialsId), jvmOptions, javaPath, null, prefixStartSlaveCmd,
             suffixStartSlaveCmd, launchTimeoutSeconds, null, null);
    }

    /**
     * @deprecated use {@link  SSHLauncher#SSHLauncher(String,int,String,String,String,String,String,Integer)}
     */
    @Deprecated
    public SSHLauncher(String host, int port, String credentialsId,
             String jvmOptions, String javaPath, String prefixStartSlaveCmd, String suffixStartSlaveCmd) {
        this(host, port, lookupSystemCredentials(credentialsId), jvmOptions, javaPath, null, prefixStartSlaveCmd, suffixStartSlaveCmd, null, null, null);
    }

    public static StandardUsernameCredentials lookupSystemCredentials(String credentialsId) {
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider
                        .lookupCredentials(StandardUsernameCredentials.class, Jenkins.getInstance(), ACL.SYSTEM,
                                SSH_SCHEME),
                CredentialsMatchers.withId(credentialsId)
        );
    }

    public static StandardUsernameCredentials lookupSystemCredentials(String credentialsId, String host, int port) {
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider
                        .lookupCredentials(StandardUsernameCredentials.class, Jenkins.getInstance(), ACL.SYSTEM,
                                SSH_SCHEME, new HostnamePortRequirement(host, port)),
                CredentialsMatchers.withId(credentialsId)
        );
    }

    /**
     * Constructor SSHLauncher creates a new SSHLauncher instance.
     *
     * @param host       The host to connect to.
     * @param port       The port to connect on.
     * @param credentials The credentials to connect as.
     * @param jvmOptions Options passed to the java vm.
     * @param javaPath   Path to the host jdk installation. If <code>null</code> the jdk will be auto detected or installed by the JDKInstaller.
     * @param prefixStartSlaveCmd This will prefix the start slave command. For instance if you want to execute the command with a different shell.
     * @param suffixStartSlaveCmd This will suffix the start slave command.
     * @param launchTimeoutSeconds Launch timeout in seconds
     * @param maxNumRetries The number of times to retry connection if the SSH connection is refused during initial connect
     * @param retryWaitTime The number of seconds to wait between retries
     */
    public SSHLauncher(String host, int port, StandardUsernameCredentials credentials,
                       String jvmOptions, String javaPath, String prefixStartSlaveCmd, String suffixStartSlaveCmd,
                       Integer launchTimeoutSeconds, Integer maxNumRetries, Integer retryWaitTime) {
        this(host, port, credentials, jvmOptions, javaPath, null, prefixStartSlaveCmd, suffixStartSlaveCmd, launchTimeoutSeconds, maxNumRetries, retryWaitTime);
    }

    /** @deprecated Use {@link #SSHLauncher(String, int, StandardUsernameCredentials, String, String, String, String, Integer, Integer, Integer)} instead. */
    @Deprecated
    public SSHLauncher(String host, int port, StandardUsernameCredentials credentials,
             String jvmOptions, String javaPath, String prefixStartSlaveCmd, String suffixStartSlaveCmd,
             Integer launchTimeoutSeconds) {
        this(host, port, credentials, jvmOptions, javaPath, null, prefixStartSlaveCmd, suffixStartSlaveCmd, launchTimeoutSeconds, null, null);
    }

    /** @deprecated Use {@link #SSHLauncher(String, int, StandardUsernameCredentials, String, String, String, String, Integer, Integer, Integer)} instead. */
    @Deprecated
    public SSHLauncher(String host, int port, StandardUsernameCredentials credentials,
             String jvmOptions, String javaPath, String prefixStartSlaveCmd, String suffixStartSlaveCmd) {
        this(host, port, credentials, jvmOptions, javaPath, prefixStartSlaveCmd, suffixStartSlaveCmd, null, null, null);
    }

    /** @deprecated Use {@link #SSHLauncher(String, int, StandardUsernameCredentials, String, String, String, String)} instead. */
    @Deprecated
    public SSHLauncher(String host, int port, SSHUser credentials,
             String jvmOptions, String javaPath, String prefixStartSlaveCmd, String suffixStartSlaveCmd) {
        this(host, port, (StandardUsernameCredentials) credentials, jvmOptions, javaPath, prefixStartSlaveCmd, suffixStartSlaveCmd, null, null, null);
    }

    /**
     * Constructor SSHLauncher creates a new SSHLauncher instance.
     *
     * @param host       The host to connect to.
     * @param port       The port to connect on.
     * @param username   The username to connect as.
     * @param password   The password to connect with.
     * @param privatekey The ssh privatekey to connect with.
     * @param jvmOptions Options passed to the java vm.
     * @param javaPath   Path to the host jdk installation. If <code>null</code> the jdk will be auto detected or installed by the JDKInstaller.
     * @param prefixStartSlaveCmd This will prefix the start slave command. For instance if you want to execute the command with a different shell.
     * @param suffixStartSlaveCmd This will suffix the start slave command.
     * @deprecated use the {@link StandardUsernameCredentials} based version
     */
    @Deprecated
    public SSHLauncher(String host, int port, String username, String password, String privatekey,
             String jvmOptions, String javaPath, String prefixStartSlaveCmd, String suffixStartSlaveCmd) {
        this(host, port, username, password, privatekey, jvmOptions, javaPath, null, prefixStartSlaveCmd,
                                                                                     suffixStartSlaveCmd);
    }

    /**
     * Constructor SSHLauncher creates a new SSHLauncher instance.
     *
     * @param host       The host to connect to.
     * @param port       The port to connect on.
     * @param username   The username to connect as.
     * @param password   The password to connect with.
     * @param privatekey The ssh privatekey to connect with.
     * @param jvmOptions Options passed to the java vm.
     * @param javaPath   Path to the host jdk installation. If <code>null</code> the jdk will be auto detected or installed by the JDKInstaller.
     * @param jdkInstaller The jdk installer that will be used if no java vm is found on the specified host. If <code>null</code> the {@link DefaultJDKInstaller} will be used.
     * @param prefixStartSlaveCmd This will prefix the start slave command. For instance if you want to execute the command with a different shell.
     * @param suffixStartSlaveCmd This will suffix the start slave command.
     * @deprecated use the {@link StandardUsernameCredentials} based version
     */
    @Deprecated
    public SSHLauncher(String host, int port, String username, String password, String privatekey, String jvmOptions,
                                    String javaPath, JDKInstaller jdkInstaller, String prefixStartSlaveCmd, String suffixStartSlaveCmd) {
        this.host = host;
        this.jvmOptions = fixEmpty(jvmOptions);
        this.port = port == 0 ? 22 : port;
        this.username = fixEmpty(username);
        this.password = Secret.fromString(fixEmpty(password));
        this.privatekey = fixEmpty(privatekey);
        this.credentials = null;
        this.credentialsId = null;
        this.javaPath = fixEmpty(javaPath);
        if (jdkInstaller != null) {
            this.jdk = jdkInstaller;
        }
        this.prefixStartSlaveCmd = fixEmpty(prefixStartSlaveCmd);
        this.suffixStartSlaveCmd = fixEmpty(suffixStartSlaveCmd);
        this.launchTimeoutSeconds = null;
        this.maxNumRetries = null;
        this.retryWaitTime = null;
    }

    /**
     * Constructor SSHLauncher creates a new SSHLauncher instance.
     *
     * @param host       The host to connect to.
     * @param port       The port to connect on.
     * @param credentials The credentials to connect as.
     * @param jvmOptions Options passed to the java vm.
     * @param javaPath   Path to the host jdk installation. If <code>null</code> the jdk will be auto detected or installed by the JDKInstaller.
     * @param jdkInstaller The jdk installer that will be used if no java vm is found on the specified host. If <code>null</code> the {@link DefaultJDKInstaller} will be used.
     * @param prefixStartSlaveCmd This will prefix the start slave command. For instance if you want to execute the command with a different shell.
     * @param suffixStartSlaveCmd This will suffix the start slave command.
     *                            @deprecated
     */
    @Deprecated
    public SSHLauncher(String host, int port, StandardUsernameCredentials credentials, String jvmOptions,
                                    String javaPath, JDKInstaller jdkInstaller, String prefixStartSlaveCmd, String suffixStartSlaveCmd) {
        this(host, port, credentials, jvmOptions, javaPath, jdkInstaller, prefixStartSlaveCmd, suffixStartSlaveCmd, null, null, null);
    }

    /**
     * Constructor SSHLauncher creates a new SSHLauncher instance.
     *
     * @param host       The host to connect to.
     * @param port       The port to connect on.
     * @param credentials The credentials to connect as.
     * @param jvmOptions Options passed to the java vm.
     * @param javaPath   Path to the host jdk installation. If <code>null</code> the jdk will be auto detected or installed by the JDKInstaller.
     * @param jdkInstaller The jdk installer that will be used if no java vm is found on the specified host. If <code>null</code> the {@link DefaultJDKInstaller} will be used.
     * @param prefixStartSlaveCmd This will prefix the start slave command. For instance if you want to execute the command with a different shell.
     * @param suffixStartSlaveCmd This will suffix the start slave command.
     * @param launchTimeoutSeconds Launch timeout in seconds
     * @param maxNumRetries The number of times to retry connection if the SSH connection is refused during initial connect
     * @param retryWaitTime The number of seconds to wait between retries
     */
    public SSHLauncher(String host, int port, StandardUsernameCredentials credentials, String jvmOptions,
                                    String javaPath, JDKInstaller jdkInstaller, String prefixStartSlaveCmd,
                                    String suffixStartSlaveCmd, Integer launchTimeoutSeconds, Integer maxNumRetries, Integer retryWaitTime) {
        this.host = host;
        this.jvmOptions = fixEmpty(jvmOptions);
        this.port = port == 0 ? 22 : port;
        this.username = null;
        this.password = null;
        this.privatekey = null;
        this.credentials = credentials;
        this.credentialsId = credentials == null ? null : credentials.getId();
        this.javaPath = fixEmpty(javaPath);
        if (jdkInstaller != null) {
            this.jdk = jdkInstaller;
        }
        this.prefixStartSlaveCmd = fixEmpty(prefixStartSlaveCmd);
        this.suffixStartSlaveCmd = fixEmpty(suffixStartSlaveCmd);
        this.launchTimeoutSeconds = launchTimeoutSeconds == null || launchTimeoutSeconds <= 0 ? null : launchTimeoutSeconds;
        this.maxNumRetries = maxNumRetries != null && maxNumRetries > 0 ? maxNumRetries : 0;
        this.retryWaitTime = retryWaitTime != null && retryWaitTime > 0 ? retryWaitTime : 0;
    }

    /** @deprecated Use {@link #SSHLauncher(String, int, StandardUsernameCredentials, String, String, JDKInstaller, String, String)} instead. */
    @Deprecated
    public SSHLauncher(String host, int port, SSHUser credentials, String jvmOptions,
                                    String javaPath, JDKInstaller jdkInstaller, String prefixStartSlaveCmd, String suffixStartSlaveCmd) {
        this(host, port, (StandardUsernameCredentials) credentials, jvmOptions, javaPath, jdkInstaller, prefixStartSlaveCmd, suffixStartSlaveCmd);
    }

    public SSHLauncher(String host, int port, String username, String password, String privatekey, String jvmOptions) {
        this(host,port,username,password,privatekey,jvmOptions,null, null, null);
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public StandardUsernameCredentials getCredentials() {
        String credentialsId = this.credentialsId == null
                ? (this.credentials == null ? null : this.credentials.getId())
                : this.credentialsId;
        try {
            // only ever want from the system
            // lookup every time so that we always have the latest
            StandardUsernameCredentials credentials = credentialsId != null ? 
                    SSHLauncher.lookupSystemCredentials(credentialsId) : null;
            if (credentials != null) {
                this.credentials = credentials;
                return credentials;
            }
        } catch (Throwable t) {
            // ignore
        }
        if (credentials == null) {
            if (credentialsId == null && (username != null || password != null || privatekey != null)) {
                credentials = upgrade(username, password, privatekey, host);
                this.credentialsId = credentials.getId();
            }
        }

        return this.credentials;
    }

    /**
     * Take the legacy local credential configuration and create an equivalent global {@link StandardUsernameCredentials}.
     */
    @NonNull
    static synchronized StandardUsernameCredentials upgrade(String username, Secret password, String privatekey, String description) {
        username = StringUtils.isEmpty(username) ? System.getProperty("user.name") : username;

        StandardUsernameCredentials u = retrieveExistingCredentials(username, password, privatekey);
        if (u != null) return u;

        // no matching, so make our own.
        if (StringUtils.isEmpty(privatekey) && (password == null || StringUtils.isEmpty(password.getPlainText()))) {
            // no private key nor password set, must be user's own SSH key
            u = new BasicSSHUserPrivateKey(CredentialsScope.SYSTEM, null, username, new BasicSSHUserPrivateKey.UsersPrivateKeySource(), null, description);
        } else if (StringUtils.isNotEmpty(privatekey)) {
            u = new BasicSSHUserPrivateKey(CredentialsScope.SYSTEM, null, username, new BasicSSHUserPrivateKey.FileOnMasterPrivateKeySource(privatekey), password == null ? null : password.getEncryptedValue(),
                    MessageFormat.format("{0} - key file: {1}", description, privatekey));
        } else {
            u = new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM, null, description, username, password == null ? null : password.getEncryptedValue());
        }

        final SecurityContext securityContext = ACL.impersonate(ACL.SYSTEM);
        try {
            CredentialsStore s = CredentialsProvider.lookupStores(Jenkins.getInstance()).iterator().next();
            try {
                s.addCredentials(Domain.global(), u);
                return u;
            } catch (IOException e) {
                // ignore
            }
        } finally {
            SecurityContextHolder.setContext(securityContext);
        }
        return u;
    }

    private static StandardUsernameCredentials retrieveExistingCredentials(String username, final Secret password,
                                                                           String privatekey) {
        final String privatekeyContent = getPrivateKeyContent(password, privatekey);
        return CredentialsMatchers.firstOrNull(CredentialsProvider
                .lookupCredentials(StandardUsernameCredentials.class, Hudson.getInstance(), ACL.SYSTEM,
                        SSH_SCHEME), allOf(
                withUsername(username),
                new CredentialsMatcher() {
            public boolean matches(@NonNull Credentials item) {
                if (item instanceof StandardUsernamePasswordCredentials
                        && password != null
                        && StandardUsernamePasswordCredentials.class.cast(item).getPassword().equals(password)) {
                    return true;
                }
                if (privatekeyContent != null && item instanceof SSHUserPrivateKey) {
                    for (String key : SSHUserPrivateKey.class.cast(item).getPrivateKeys()) {
                        if (pemKeyEquals(key, privatekeyContent)) {
                            return true;
                        }
                    }
                }
                return false;
            }
        }));
    }

    /**
     * Returns {@code true} if they two keys are the same. There are two levels of comparison: the first is a simple
     * string comparison with all whitespace removed. If that fails then the Base64 decoded bytes of the first
     * PEM entity will be compared (to allow for comments in the key outside the PEM boundaries)
     *
     * @param key1 the first key
     * @param key2 the second key
     * @return {@code true} if they two keys are the same.
     */
    private static boolean pemKeyEquals(String key1, String key2) {
        key1 = StringUtils.trim(key1);
        key2 = StringUtils.trim(key2);
        return StringUtils.equals(key1.replaceAll("\\s+", ""), key2.replace("\\s+", ""))
                || Arrays.equals(quickNDirtyExtract(key1), quickNDirtyExtract(key2));
    }

    /**
     * Extract the bytes of the first PEM encoded key in a string. This is a quick and dirty method just to
     * establish if two keys are equal, we do not do any serious decoding of the key and this method could give "issues"
     * but should be very unlikely to result in a false positive match.
     *
     * @param key the key to extract.
     * @return the base64 decoded bytes from the key after discarding the key type and any header information.
     */
    private static byte[] quickNDirtyExtract(String key) {
        StringBuilder builder = new StringBuilder(key.length());
        boolean begin = false;
        boolean header = false;
        for (String line : StringUtils.split(key, "\n")) {
            line = line.trim();
            if (line.startsWith("---") && line.endsWith("---")) {
                if (begin && line.contains("---END")) {
                    break;
                }
                if (!begin && line.contains("---BEGIN")) {
                    header = true;
                    begin = true;
                    continue;
                }
            }
            if (StringUtils.isBlank(line)) {
                header = false;
                continue;
            }
            if (!header) {
                builder.append(line);
            }
        }
        return Base64.decodeBase64(builder.toString());
    }

    private static String getPrivateKeyContent(Secret password, String privatekey) {
        privatekey = Util.fixEmpty(privatekey);
        if (privatekey != null) {
            try {
                File key = new File(privatekey);
                if (key.exists()) {
                    if (PuTTYKey.isPuTTYKeyFile(key)) {
                        return Util.fixEmptyAndTrim(new PuTTYKey(key, password.getPlainText()).toOpenSSH());
                    } else {
                        return Util.fixEmptyAndTrim(FileUtils.readFileToString(key));
                    }
                }
            } catch (Throwable t) {
                LOGGER.warning("invalid private key file " + privatekey);
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isLaunchSupported() {
        return true;
    }

    /**
     * Gets the JVM Options used to launch the slave JVM.
     * @return
     */
    public String getJvmOptions() {
        return jvmOptions == null ? "" : jvmOptions;
    }

    /**
     * Gets the optionnal java command to use to launch the slave JVM.
     * @return
     */
    public String getJavaPath() {
        return javaPath == null ? "" : javaPath;
    }

    /**
     * Gets the formatted current time stamp.
     *
     * @return the formatted current time stamp.
     */
    protected String getTimestamp() {
        return String.format("[%1$tD %1$tT]", new Date());
    }

    /**
     * Returns the remote root workspace (without trailing slash).
     *
     * @param computer The slave computer to get the root workspace of.
     *
     * @return the remote root workspace (without trailing slash).
     */
    @CheckForNull
    private static String getWorkingDirectory(SlaveComputer computer) {
        return getWorkingDirectory(computer.getNode());
    }

    @CheckForNull
    private static String getWorkingDirectory(@CheckForNull Slave slave) {
        if (slave == null) {
            return null;
        }
        String workingDirectory = slave.getRemoteFS();
        while (workingDirectory.endsWith("/")) {
            workingDirectory = workingDirectory.substring(0, workingDirectory.length() - 1);
        }
        return workingDirectory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void launch(final SlaveComputer computer, final TaskListener listener) throws InterruptedException {
        connection = new Connection(host, port);
        ExecutorService executorService = Executors.newSingleThreadExecutor(
                new NamingThreadFactory(Executors.defaultThreadFactory(), "SSHLauncher.launch for '" + computer.getName() + "' node"));
        Set<Callable<Boolean>> callables = new HashSet<Callable<Boolean>>();
        callables.add(new Callable<Boolean>() {
            public Boolean call() throws InterruptedException {
                Boolean rval = Boolean.FALSE;
                try {

                    openConnection(listener);

                    verifyNoHeaderJunk(listener);
                    reportEnvironment(listener);

                    String java = resolveJava(computer, listener);

                    final String workingDirectory = getWorkingDirectory(computer);
                    if (workingDirectory == null) {
                        listener.error("Cannot get the working directory for " + computer);
                        return Boolean.FALSE;
                    }
                    copySlaveJar(listener, workingDirectory);

                    startSlave(computer, listener, java, workingDirectory);

                    PluginImpl.register(connection);
                    rval = Boolean.TRUE;
                } catch (RuntimeException e) {
                    e.printStackTrace(listener.error(Messages.SSHLauncher_UnexpectedError()));
                } catch (Error e) {
                    e.printStackTrace(listener.error(Messages.SSHLauncher_UnexpectedError()));
                } catch (IOException e) {
                    e.printStackTrace(listener.getLogger());
                } finally {
                    return rval;
                }
            }
        });

        final Node node = computer.getNode();
        final String nodeName = node != null ? node.getNodeName() : "unknown";
        try {
            long time = System.currentTimeMillis();
            List<Future<Boolean>> results;
            if (this.getLaunchTimeoutMillis() > 0) {
                results = executorService.invokeAll(callables, this.getLaunchTimeoutMillis(), TimeUnit.MILLISECONDS);
            } else {
                results = executorService.invokeAll(callables);
            }
            long duration = System.currentTimeMillis() - time;
            Boolean res;
            try {
                res = results.get(0).get();
            } catch (ExecutionException e) {
                res = Boolean.FALSE;
            }
            if (!res) {
                System.out.println(Messages.SSHLauncher_LaunchFailedDuration(getTimestamp(),
                        nodeName, host, duration));
                listener.getLogger().println(getTimestamp() + " Launch failed - cleaning up connection");
                cleanupConnection(listener);
            } else {
                System.out.println(Messages.SSHLauncher_LaunchCompletedDuration(getTimestamp(),
                        nodeName, host, duration));
            }
            executorService.shutdown();
        } catch (InterruptedException e) {
            System.out.println(Messages.SSHLauncher_LaunchFailed(getTimestamp(),
                    nodeName, host));
        }

    }

    /**
     * Called to terminate the SSH connection. Used liberally when we back out from an error.
     */
    private void cleanupConnection(TaskListener listener) {
        // we might be called multiple times from multiple finally/catch block, 
        if (connection!=null) {
            connection.close();
            connection = null;
            listener.getLogger().println(Messages.SSHLauncher_ConnectionClosed(getTimestamp()));
        }
    }

    /**
     * return javaPath if specified in the configuration.
     * Finds local Java, and if none exist, install one.
     */
    protected String resolveJava(SlaveComputer computer, TaskListener listener) throws InterruptedException, IOException2 {

        if (StringUtils.isNotBlank(javaPath)) {
            return expandExpression(computer, javaPath);
        }

        final String workingDirectory = getWorkingDirectory(computer);
        if (workingDirectory == null) {
            throw new IOException2("Cannot retrieve a working directory of " + computer, null);
        }

        List<String> tried = new ArrayList<String>();
        for (JavaProvider provider : JavaProvider.all()) {
            for (String javaCommand : provider.getJavas(computer, listener, connection)) {
                LOGGER.fine("Trying Java at "+javaCommand);
                try {
                    tried.add(javaCommand);
                    return checkJavaVersion(listener, javaCommand);
                } catch (IOException e) {
                    LOGGER.log(FINE, "Failed to check the Java version",e);
                    // try the next one
                }
            }
        }

        // attempt auto JDK installation
        try {
            return attemptToInstallJDK(listener, workingDirectory);
        } catch (IOException e) {
            throw new IOException2("Could not find any known supported java version in "+tried+", and we also failed to install JDK as a fallback",e);
        }
    }

    private String expandExpression(SlaveComputer computer, String expression) {
        return getEnvVars(computer).expand(expression);
    }

    private EnvVars getEnvVars(SlaveComputer computer) {
        final EnvVars global = getEnvVars(Jenkins.getActiveInstance());

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
     * Makes sure that SSH connection won't produce any unwanted text, which will interfere with sftp execution.
     */
    private void verifyNoHeaderJunk(TaskListener listener) throws IOException, InterruptedException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        connection.exec("true",baos);
        final String s;
        //TODO: Seems we need to retrieve the encoding from the connection destination
        try {
            s = baos.toString(Charset.defaultCharset().name());
        } catch (UnsupportedEncodingException ex) { // Should not happen
            throw new IOException("Default encoding is unsupported", ex);
        }
        
        if (s.length()!=0) {
            listener.getLogger().println(Messages.SSHLauncher_SSHHeeaderJunkDetected());
            listener.getLogger().println(s);
            throw new AbortException();
        }
    }

    private JDKInstaller getJDKInstaller() {
        return jdk!=null ? jdk : new JDKInstaller(SSHLauncher.DEFAULT_JDK, true);
    }

    /**
     * Attempts to install JDK, and return the path to Java.
     */
    private String attemptToInstallJDK(TaskListener listener, String workingDirectory) throws IOException, InterruptedException {
        ByteArrayOutputStream unameOutput = new ByteArrayOutputStream();
        if (connection.exec("uname -a",new TeeOutputStream(unameOutput,listener.getLogger()))!=0)
            throw new IOException("Failed to run 'uname' to obtain the environment");

        // guess the platform from uname output. I don't use the specific options because I'm not sure
        // if various platforms have the consistent options
        //
        // === some of the output collected ====
        // Linux bear 2.6.28-15-generic #49-Ubuntu SMP Tue Aug 18 19:25:34 UTC 2009 x86_64 GNU/Linux
        // Linux wssqe20 2.6.24-24-386 #1 Tue Aug 18 16:24:26 UTC 2009 i686 GNU/Linux
        // SunOS hudson 5.11 snv_79a i86pc i386 i86pc
        // SunOS legolas 5.9 Generic_112233-12 sun4u sparc SUNW,Sun-Fire-280R
        // CYGWIN_NT-5.1 franz 1.7.0(0.185/5/3) 2008-07-22 19:09 i686 Cygwin
        // Windows_NT WINXPIE7 5 01 586
        //        (this one is from MKS)
        
        //TODO: Seems we need to retrieve the encoding from the connection destination
        final String uname;
        try {
            uname = unameOutput.toString(Charset.defaultCharset().name());
        } catch (UnsupportedEncodingException ex) { // Should not happen
            throw new IOException("Default encoding is unsupported", ex);
        }
        Platform p = null;
        CPU cpu = null;
        if (uname.contains("GNU/Linux"))        p = Platform.LINUX;
        if (uname.contains("SunOS"))            p = Platform.SOLARIS;
        if (uname.contains("CYGWIN"))           p = Platform.WINDOWS;
        if (uname.contains("Windows_NT"))       p = Platform.WINDOWS;

        if (uname.contains("sparc"))            cpu = CPU.Sparc;
        if (uname.contains("x86_64"))           cpu = CPU.amd64;
        if (Pattern.compile("\\bi?[3-6]86\\b").matcher(uname).find())           cpu = CPU.i386;  // look for ix86 as a word

        if (p==null || cpu==null)
            throw new IOException(Messages.SSHLauncher_FailedToDetectEnvironment(uname));

        String javaDir = workingDirectory + "/jdk"; // this is where we install Java to
        String bundleFile = workingDirectory + "/" + p.bundleFileName; // this is where we download the bundle to

        SFTPClient sftp = new SFTPClient(connection);
        // wipe out and recreate the Java directory
        connection.exec("rm -rf "+javaDir,listener.getLogger());
        sftp.mkdirs(javaDir, 0755);

        URL bundle = getJDKInstaller().locate(listener, p, cpu);

        listener.getLogger().println("Installing " + JDKVERSION);
        Util.copyStreamAndClose(bundle.openStream(),new BufferedOutputStream(sftp.writeToFile(bundleFile),32*1024));
        sftp.chmod(bundleFile,0755);

        getJDKInstaller().install(new RemoteLauncher(listener,connection),p,new SFTPFileSystem(sftp),listener, javaDir,bundleFile);
        return javaDir+"/bin/java";
    }

    /**
     * Starts the slave process.
     *
     * @param computer         The computer.
     * @param listener         The listener.
     * @param java             The full path name of the java executable to use.
     * @param workingDirectory The working directory from which to start the java process.
     *
     * @throws IOException If something goes wrong.
     */
    private void startSlave(SlaveComputer computer, final TaskListener listener, String java,
                            String workingDirectory) throws IOException {
        session = connection.openSession();
        expandChannelBufferSize(session,listener);
        String cmd = "cd \"" + workingDirectory + "\" && " + java + " " + getJvmOptions() + " -jar slave.jar";

        //This will wrap the cmd with prefix commands and suffix commands if they are set.
        cmd = getPrefixStartSlaveCmd() + cmd + getSuffixStartSlaveCmd();

        listener.getLogger().println(Messages.SSHLauncher_StartingSlaveProcess(getTimestamp(), cmd));
        session.execCommand(cmd);

        session.pipeStderr(new DelegateNoCloseOutputStream(listener.getLogger()));

        try {
            computer.setChannel(session.getStdout(), session.getStdin(), listener.getLogger(), null);
        } catch (InterruptedException e) {
            session.close();
            throw new IOException2(Messages.SSHLauncher_AbortedDuringConnectionOpen(), e);
        } catch (IOException e) {
            try {
                // often times error this early means the JVM has died, so let's see if we can capture all stderr
                // and exit code
                throw new IOException2(getSessionOutcomeMessage(session,false),e);
            } catch (InterruptedException x) {
                throw (IOException)new IOException().initCause(e);
            }
        }
    }

    private void expandChannelBufferSize(Session session, TaskListener listener) {
            // see hudson.remoting.Channel.PIPE_WINDOW_SIZE for the discussion of why 1MB is in the right ball park
            // but this particular session is where all the master/slave communication will happen, so
            // it's worth using a bigger buffer to really better utilize bandwidth even when the latency is even larger
            // (and since we are draining this pipe very rapidly, it's unlikely that we'll actually accumulate this much data)
            int sz = 4;
            session.setWindowSize(sz*1024*1024);
            listener.getLogger().println("Expanded the channel window size to "+sz+"MB");
    }

    /**
     * Method copies the slave jar to the remote system.
     *
     * @param listener         The listener.
     * @param workingDirectory The directory into whihc the slave jar will be copied.
     *
     * @throws IOException If something goes wrong.
     */
    private void copySlaveJar(TaskListener listener, String workingDirectory) throws IOException, InterruptedException {
        String fileName = workingDirectory + "/slave.jar";

        listener.getLogger().println(Messages.SSHLauncher_StartingSFTPClient(getTimestamp()));
        SFTPClient sftpClient = null;
        try {
            sftpClient = new SFTPClient(connection);

            try {
                SFTPv3FileAttributes fileAttributes = sftpClient._stat(workingDirectory);
                if (fileAttributes==null) {
                    listener.getLogger().println(Messages.SSHLauncher_RemoteFSDoesNotExist(getTimestamp(),
                            workingDirectory));
                    sftpClient.mkdirs(workingDirectory, 0700);
                } else if (fileAttributes.isRegularFile()) {
                    throw new IOException(Messages.SSHLauncher_RemoteFSIsAFile(workingDirectory));
                }

                try {
                    // try to delete the file in case the slave we are copying is shorter than the slave
                    // that is already there
                    sftpClient.rm(fileName);
                } catch (IOException e) {
                    // the file did not exist... so no need to delete it!
                }

                listener.getLogger().println(Messages.SSHLauncher_CopyingSlaveJar(getTimestamp()));

                try {
                    byte[] slaveJar = new Slave.JnlpJar("slave.jar").readFully();
                    OutputStream os = sftpClient.writeToFile(fileName);
                    try {
                        os.write(slaveJar);
                    } finally {
                        os.close();
                    }
                    listener.getLogger().println(Messages.SSHLauncher_CopiedXXXBytes(getTimestamp(), slaveJar.length));
                } catch (Error error) {
                    throw error;  
                } catch (Throwable e) {
                    throw new IOException2(Messages.SSHLauncher_ErrorCopyingSlaveJarTo(fileName), e);
                }
            } catch (Error error) {
                throw error;
            } catch (Throwable e) {
                throw new IOException2(Messages.SSHLauncher_ErrorCopyingSlaveJarInto(workingDirectory), e);
            }
        } catch (IOException e) {
            if (sftpClient == null) {
                e.printStackTrace(listener.error(Messages.SSHLauncher_StartingSCPClient(getTimestamp())));
                // lets try to recover if the slave doesn't have an SFTP service
                copySlaveJarUsingSCP(listener, workingDirectory);
            } else {
                throw e;
            }
        } finally {
            if (sftpClient != null) {
                sftpClient.close();
            }
        }
    }
    /**
     * Method copies the slave jar to the remote system using scp.
     *
     * @param listener         The listener.
     * @param workingDirectory The directory into which the slave jar will be copied.
     *
     * @throws IOException If something goes wrong.
     * @throws InterruptedException If something goes wrong.
     */
    private void copySlaveJarUsingSCP(TaskListener listener, String workingDirectory) throws IOException, InterruptedException {
        SCPClient scp = new SCPClient(connection);
        try {
            // check if the working directory exists
            if (connection.exec("test -d " + workingDirectory ,listener.getLogger())!=0) {
                listener.getLogger().println(
                        Messages.SSHLauncher_RemoteFSDoesNotExist(getTimestamp(), workingDirectory));
                // working directory doesn't exist, lets make it.
                if (connection.exec("mkdir -p " + workingDirectory, listener.getLogger())!=0) {
                    listener.getLogger().println("Failed to create "+workingDirectory);
                }
            }

            // delete the slave jar as we do with SFTP
            connection.exec("rm " + workingDirectory + "/slave.jar", new NullStream());

            // SCP it to the slave. hudson.Util.ByteArrayOutputStream2 doesn't work for this. It pads the byte array.
            listener.getLogger().println(Messages.SSHLauncher_CopyingSlaveJar(getTimestamp()));
            scp.put(new Slave.JnlpJar("slave.jar").readFully(), "slave.jar", workingDirectory, "0644");
        } catch (IOException e) {
            throw new IOException2(Messages.SSHLauncher_ErrorCopyingSlaveJarInto(workingDirectory), e);
        }
    }

    protected void reportEnvironment(TaskListener listener) throws IOException, InterruptedException {
        listener.getLogger().println(Messages._SSHLauncher_RemoteUserEnvironment(getTimestamp()));
        connection.exec("set",listener.getLogger());
    }

    @NonNull
    private String checkJavaVersion(TaskListener listener, String javaCommand) throws IOException, InterruptedException {
        listener.getLogger().println(Messages.SSHLauncher_CheckingDefaultJava(getTimestamp(),javaCommand));
        StringWriter output = new StringWriter();   // record output from Java

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        connection.exec(javaCommand + " "+getJvmOptions() + " -version",out);
        //TODO: Seems we need to retrieve the encoding from the connection destination
        BufferedReader r = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(out.toByteArray()), Charset.defaultCharset()));
        final String result = checkJavaVersion(listener.getLogger(), javaCommand, r, output);

        if(null == result) {
            listener.getLogger().println(Messages.SSHLauncher_UknownJavaVersion(javaCommand));
            listener.getLogger().println(output);
            throw new IOException(Messages.SSHLauncher_UknownJavaVersion(javaCommand));
        } else {
            return result;
        }
    }

    // XXX switch to standard method in 1.479+
    /**
     * Given the output of "java -version" in <code>r</code>, determine if this
     * version of Java is supported. This method has default visiblity for testing.
     *
     * @param logger
     *            where to log the output
     * @param javaCommand
     *            the command executed, used for logging
     * @param r
     *            the output of "java -version"
     * @param output
     *            copy the data from <code>r</code> into this output buffer
     */
    @CheckForNull
    protected String checkJavaVersion(final PrintStream logger, String javaCommand,
            final BufferedReader r, final StringWriter output)
            throws IOException {
        String line;
        while (null != (line = r.readLine())) {
            output.write(line);
            output.write("\n");
            line = line.toLowerCase(Locale.ENGLISH);
            if (line.startsWith("java version \"")
                    || line.startsWith("openjdk version \"")) {
                final String versionStr = line.substring(
                        line.indexOf('\"') + 1, line.lastIndexOf('\"'));
                logger.println(Messages.SSHLauncher_JavaVersionResult(
                        getTimestamp(), javaCommand, versionStr));

                // parse as a number and we should be OK as all we care about is up through the first dot.
                try {
                    final Number version =
                        NumberFormat.getNumberInstance(Locale.US).parse(versionStr);
                    if(version.doubleValue() < 1.5) {
                        throw new IOException(Messages
                                .SSHLauncher_NoJavaFound(line));
                    }
                } catch(final ParseException e) {
                    throw new IOException(Messages.SSHLauncher_NoJavaFound(line));
                }
                return javaCommand;
            }
        }
        return null;
    }

    protected void openConnection(TaskListener listener) throws IOException, InterruptedException {
        PrintStream logger = listener.getLogger();
        logger.println(Messages.SSHLauncher_OpeningSSHConnection(getTimestamp(), host + ":" + port));
        connection.setTCPNoDelay(true);

        int maxNumRetries = this.maxNumRetries == null || this.maxNumRetries < 0 ? 0 : this.maxNumRetries;

        for (int i = 0; i <= maxNumRetries; i++) {
            try {
                connection.connect();
                break;
            } catch (IOException ioexception) {
                String message = "";
                Throwable cause = ioexception.getCause();
                if (cause != null) {
                    message = cause.getMessage();
                    logger.println(message);
                }
                if (cause == null || !isRecoverable(message)) {
                    throw ioexception;
                }
                if (maxNumRetries - i > 0) {
                    logger.println("SSH Connection failed with IOException: \"" + message
                                                         + "\", retrying in " + retryWaitTime + " seconds.  There are "
                                                         + (maxNumRetries - i) + " more retries left.");
                } else {
                    logger.println("SSH Connection failed with IOException: \"" + message + "\".");
                    throw ioexception;
                }
            }
            Thread.sleep(TimeUnit.SECONDS.toMillis(retryWaitTime));
        }

        StandardUsernameCredentials credentials = getCredentials();
        if (credentials == null) {
            throw new AbortException("Cannot find SSH User credentials with id: " + credentialsId);
        }
        if (SSHAuthenticator.newInstance(connection, credentials).authenticate(listener)
                && connection.isAuthenticationComplete()) {
            logger.println(Messages.SSHLauncher_AuthenticationSuccessful(getTimestamp()));
        } else {
            logger.println(Messages.SSHLauncher_AuthenticationFailed(getTimestamp()));
            throw new AbortException(Messages.SSHLauncher_AuthenticationFailedException());
        }
    }

    private boolean isRecoverable(String message) {
        for (String s : RECOVERABLE_FAILURES) {
            if (message.startsWith(s)) return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void afterDisconnect(SlaveComputer slaveComputer, final TaskListener listener) {
        if (connection != null) {
            boolean connectionLost = reportTransportLoss(connection, listener);
            if (session!=null) {
                // give the process 3 seconds to write out its dying message before we cut the loss
                // and give up on this process. if the slave process had JVM crash, OOME, or any other
                // critical problem, this will allow us to capture that.
                // exit code is also an useful info to figure out why the process has died.
                try {
                    listener.getLogger().println(getSessionOutcomeMessage(session,connectionLost));
                    session.getStdout().close();
                    session.close();
                } catch (Throwable t) {
                    t.printStackTrace(listener.error(Messages.SSHLauncher_ErrorWhileClosingConnection()));
                }
                session = null;
            }

            Slave n = slaveComputer.getNode();
            if (n != null && !connectionLost) {
                String workingDirectory = getWorkingDirectory(n);
                final String fileName = workingDirectory + "/slave.jar";
                Future<?> tidyUp = Computer.threadPoolForRemoting.submit(new Runnable() {
                    public void run() {
                        // this would fail if the connection is already lost, so we want to check that.
                        // TODO: Connection class should expose whether it is still connected or not.

                        SFTPv3Client sftpClient = null;
                        try {
                            sftpClient = new SFTPv3Client(connection);
                            sftpClient.rm(fileName);
                        } catch (Exception e) {
                            if (sftpClient == null) {// system without SFTP
                                try {
                                    connection.exec("rm " + fileName, listener.getLogger());
                                } catch (Error error) {
                                    throw error;
                                } catch (Throwable x) {
                                    x.printStackTrace(listener.error(Messages.SSHLauncher_ErrorDeletingFile(getTimestamp())));
                                    // We ignore other Exception types
                                }
                            } else {
                                e.printStackTrace(listener.error(Messages.SSHLauncher_ErrorDeletingFile(getTimestamp())));
                            }
                        } finally {
                            if (sftpClient != null) {
                                sftpClient.close();
                            }
                        }
                    }
                });
                try {
                    // the delete is best effort only and if it takes longer than 60 seconds - or the launch 
                    // timeout (if specified) - then we should just give up and leave the file there.
                    tidyUp.get(launchTimeoutSeconds == null ? 60 : launchTimeoutSeconds, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace(listener.error(Messages.SSHLauncher_ErrorDeletingFile(getTimestamp())));
                    // we should either re-apply our interrupt flag or propagate... we don't want to propagate, so...
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    e.printStackTrace(listener.error(Messages.SSHLauncher_ErrorDeletingFile(getTimestamp())));
                } catch (TimeoutException e) {
                    e.printStackTrace(listener.error(Messages.SSHLauncher_ErrorDeletingFile(getTimestamp())));
                } finally {
                    if (!tidyUp.isDone()) {
                        tidyUp.cancel(true);
                    }
                }
            }

            PluginImpl.unregister(connection);
            cleanupConnection(listener);
        }
    }

    /**
     * If the SSH connection as a whole is lost, report that information.
     */
    private boolean reportTransportLoss(Connection c, TaskListener listener) {
        // TODO: switch to Connection.getReasonClosedCause() post build217-jenkins-8
        // in the mean time, rely on reflection to get to the object

        TransportManager tm = null;
        try {
            Field f = Connection.class.getDeclaredField("tm");
            f.setAccessible(true);
            tm = (TransportManager) f.get(c);
        } catch (NoSuchFieldException e) {
            e.printStackTrace(listener.error("Failed to get to TransportManager"));
        } catch (IllegalAccessException e) {
            e.printStackTrace(listener.error("Failed to get to TransportManager"));
        }

        if (tm==null) {
            listener.error("Couldn't get to TransportManager.");
            return false;
        }

        Throwable cause = tm.getReasonClosedCause();
        if (cause!=null) {
            cause.printStackTrace(listener.error("Socket connection to SSH server was lost"));
        }

        return cause!=null;
    }

    /**
     * Find the exit code or exit status, which are differentiated in SSH protocol.
     */
    private String getSessionOutcomeMessage(Session session, boolean isConnectionLost) throws InterruptedException {
        session.waitForCondition(ChannelCondition.EXIT_STATUS | ChannelCondition.EXIT_SIGNAL, 3000);

        Integer exitCode = session.getExitStatus();
        if (exitCode != null)
            return "Slave JVM has terminated. Exit code=" + exitCode;

        String sig = session.getExitSignal();
        if (sig != null)
            return "Slave JVM has terminated. Exit signal=" + sig;

        if (isConnectionLost)
            return "Slave JVM has not reported exit code before the socket was lost";

        return "Slave JVM has not reported exit code. Is it still running?";
    }

    /**
     * Getter for property 'host'.
     *
     * @return Value for property 'host'.
     */
    public String getHost() {
        return host;
    }

    /**
     * Getter for property 'port'.
     *
     * @return Value for property 'port'.
     */
    public int getPort() {
        return port;
    }

    /**
     * Getter for property 'username'.
     *
     * @return Value for property 'username'.
     * @deprecated
     */
    @Deprecated
    public String getUsername() {
        return username;
    }

    /**
     * Getter for property 'password'.
     *
     * @return Value for property 'password'.
     * @deprecated
     */
    @Deprecated
    public String getPassword() {
        return password!=null ? Secret.toString(password) : null;
    }

    /**
     * Getter for property 'privatekey'.
     *
     * @return Value for property 'privatekey'.
     * @deprecated
     */
    @Deprecated
    public String getPrivatekey() {
        return privatekey;
    }

    public Connection getConnection() {
        return connection;
    }

    public String getPrefixStartSlaveCmd() {
        return prefixStartSlaveCmd == null ? "" : prefixStartSlaveCmd;
    }

    public String getSuffixStartSlaveCmd() {
        return suffixStartSlaveCmd == null ? "" : suffixStartSlaveCmd;
    }

    /**
     * Getter for property 'launchTimeoutSeconds'
     *
     * @return launchTimeoutSeconds
     */
    public Integer getLaunchTimeoutSeconds() {
        return launchTimeoutSeconds;
    }

    private long getLaunchTimeoutMillis() {
        return launchTimeoutSeconds == null ? 0L : TimeUnit.SECONDS.toMillis(launchTimeoutSeconds);
    }

    /**
     * Getter for property 'maxNumRetries'
     *
     * @return maxNumRetries
     */
    public Integer getMaxNumRetries() {
        return maxNumRetries == null || maxNumRetries < 0 ? Integer.valueOf(0) : maxNumRetries;
    }

    /**
     * Getter for property 'retryWaitTime'
     *
     * @return retryWaitTime
     */
    public Integer getRetryWaitTime() {
        return retryWaitTime;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ComputerLauncher> {

        // TODO move the authentication storage to descriptor... see SubversionSCM.java

        /**
         * {@inheritDoc}
         */
        public String getDisplayName() {
            return Messages.SSHLauncher_DescriptorDisplayName();
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
                n = Jenkins.getActiveInstance().getDescriptor(SSHConnector.class).getHelpFile(fieldName);
            return n;
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context,
                                                     @QueryParameter String host,
                                                     @QueryParameter String port,
                                                     @QueryParameter String credentialsId) {
            AccessControlled _context = (context instanceof AccessControlled ? (AccessControlled) context : Jenkins.getInstance());
            if (_context == null || !_context.hasPermission(Computer.CONFIGURE)) {
                return new StandardUsernameListBoxModel()
                        .includeCurrentValue(credentialsId);
            }
            try {
                int portValue = Integer.parseInt(port);
                return new StandardUsernameListBoxModel()
                        .includeMatchingAs(
                                ACL.SYSTEM,
                                Jenkins.getActiveInstance(),
                                StandardUsernameCredentials.class,
                                Collections.<DomainRequirement>singletonList(
                                        new HostnamePortRequirement(host, portValue)
                                ),
                                SSHAuthenticator.matcher(Connection.class))
                        .includeCurrentValue(credentialsId); // always add the current value last in case already present
            } catch (NumberFormatException ex) {
                return new StandardUsernameListBoxModel()
                        .includeCurrentValue(credentialsId);
            }
        }

        public FormValidation doCheckCredentialsId(@AncestorInPath ItemGroup context,
                                                   @QueryParameter String host,
                                                   @QueryParameter String port,
                                                   @QueryParameter String value) {
            AccessControlled _context =
                    (context instanceof AccessControlled ? (AccessControlled) context : Jenkins.getInstance());
            if (_context == null || !_context.hasPermission(Computer.CONFIGURE)) {
                return FormValidation.ok(); // no need to alarm a user that cannot configure
            }
            try {
                int portValue = Integer.parseInt(port);
                for (ListBoxModel.Option o : CredentialsProvider
                        .listCredentials(StandardUsernameCredentials.class, context, ACL.SYSTEM,
                                Collections.<DomainRequirement>singletonList(
                                        new HostnamePortRequirement(host, portValue)
                                ),
                                SSHAuthenticator.matcher(Connection.class))) {
                    if (StringUtils.equals(value, o.value)) {
                        return FormValidation.ok();
                    }
                }
            } catch (NumberFormatException e) {
                return FormValidation.warning(e, Messages.SSHLauncher_PortNotANumber());
            }
            return FormValidation.error(Messages.SSHLauncher_SelectedCredentialsMissing());
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
    }

    @Extension
    public static class DefaultJavaProvider extends JavaProvider {
        @Override
        public List<String> getJavas(SlaveComputer computer, TaskListener listener, Connection connection) {
            List<String> javas = new ArrayList<String>(Arrays.asList(
                    "java",
                    "/usr/bin/java",
                    "/usr/java/default/bin/java",
                    "/usr/java/latest/bin/java",
                    "/usr/local/bin/java",
                    "/usr/local/java/bin/java")); // this is where we attempt to auto-install

            String workingDirectory = getWorkingDirectory(computer);
            if (workingDirectory != null) {
                javas.add(workingDirectory + "/jdk/bin/java");
            }
               
            final Node node = computer.getNode();
            DescribableList<NodeProperty<?>,NodePropertyDescriptor> list = 
                    node != null ? node.getNodeProperties() : null;
            if (list != null) {
                Descriptor jdk = Jenkins.getActiveInstance().getDescriptorByType(JDK.DescriptorImpl.class);
                for (NodeProperty prop : list) {
                    if (prop instanceof EnvironmentVariablesNodeProperty) {
                        EnvVars env = ((EnvironmentVariablesNodeProperty)prop).getEnvVars();
                        if (env != null && env.containsKey("JAVA_HOME"))
                            javas.add(env.get("JAVA_HOME") + "/bin/java");
                    }
                    else if (prop instanceof ToolLocationNodeProperty) {
                        for (ToolLocation tool : ((ToolLocationNodeProperty)prop).getLocations())
                            if (tool.getType() == jdk)
                                javas.add(tool.getHome() + "/bin/java");
                    }
                }
            }
            return javas;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(SSHLauncher.class.getName());

    private static class DelegateNoCloseOutputStream extends OutputStream {
        private OutputStream out;

        public DelegateNoCloseOutputStream(OutputStream out) {
            this.out = out;
        }

        @Override
        public void write(int b) throws IOException {
            if (out != null) out.write(b);
        }

        @Override
        public void close() throws IOException {
            out = null;
        }

        @Override
        public void flush() throws IOException {
            if (out != null) out.flush();
        }

        @Override
        public void write(byte[] b) throws IOException {
            if (out != null) out.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (out != null) out.write(b, off, len);
        }
    }

//    static {
//        com.trilead.ssh2.log.Logger.enabled = true;
//        com.trilead.ssh2.log.Logger.logger = new DebugLogger() {
//            public void log(int level, String className, String message) {
//                System.out.println(className+"\n"+message);
//            }
//        };
//    }
}
