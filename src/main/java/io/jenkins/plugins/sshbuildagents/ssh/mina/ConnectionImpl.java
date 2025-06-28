/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sshbuildagents.ssh.mina;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.util.Secret;
import io.jenkins.plugins.sshbuildagents.ssh.Connection;
import io.jenkins.plugins.sshbuildagents.ssh.ServerHostKeyVerifier;
import io.jenkins.plugins.sshbuildagents.ssh.ShellChannel;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.scp.client.DefaultScpClient;

/**
 * Implements {@link Connection} using the Apache Mina SSHD library
 * https://github.com/apache/mina-sshd
 *
 */
public class ConnectionImpl implements Connection {
    /** The number of heartbeat packets lost before closing the connection. */
    public static final int HEARTBEAT_MAX_RETRY = 6;

    /** The size of the SSH window size in bytes. */
    public static final long WINDOW_SIZE = 4L * 1024 * 1024;

    /** The time in seconds to wait before sending a keepalive packet. */
    public static final int HEARTBEAT_INTERVAL = 10;

    /** The time in minutes to wait before closing the session if no command is executed. */
    public static final int IDLE_SESSION_TIMEOUT = 60;

    /** The standard output stream of the channel. */
    private OutputStream stdout = System.out;

    /** The standard error stream of the channel. */
    private OutputStream stderr = System.err;

    /** The SSH client used for the connection. */
    private SshClient client;

    /** The server host key verifier. */
    // TODO implement the host key verifier
    @SuppressWarnings("unused")
    private ServerHostKeyVerifier hostKeyVerifier;

    /** The timeout in milliseconds for the connection and authentication. */
    private long timeoutMillis = 30000;

    /** The credentials used for authentication. */
    private StandardUsernameCredentials credentials;

    /** The host to connect to. */
    private final String host;

    /** The port to connect to. */
    private final int port;

    /** The maximum number of retries for connection attempts. */
    private int maxNumRetries = 1;

    /** The time in seconds to wait between retries. */
    private int retryWaitTime = 10;

    /** The working directory for the SSH session. */
    // TODO implement the working directory
    @SuppressWarnings("unused")
    private String workingDirectory;

    /** The TCP_NODELAY flag. */
    private boolean tcpNoDelay = true;

    /** The SSH session. */
    private ClientSession session;

    /**
     * Constructor to create a new SSH connection.
     *
     * @param host The hostname or IP address of the SSH server.
     * @param port The port number of the SSH server.
     */
    public ConnectionImpl(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /** {@inheritDoc} */
    @Override
    public int execCommand(String command) throws IOException {
        try (ClientSession session = connect()) {
            session.executeRemoteCommand(command, stdout, stderr, StandardCharsets.UTF_8);
        }
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public ShellChannel shellChannel() throws IOException {
        return new ShellChannelImpl(connect());
    }

    /**
     * It adds the authentication details configured to the SSH session.
     *
     * @throws IOException in case of error.
     */
    private void addAuthentication() throws IOException {
        try {
            if (credentials instanceof StandardUsernamePasswordCredentials) {
                StandardUsernamePasswordCredentials userCredentials =
                        (StandardUsernamePasswordCredentials) this.credentials;
                session.addPasswordIdentity(userCredentials.getPassword().getPlainText());
            } else if (credentials instanceof SSHUserPrivateKey) {
                SSHUserPrivateKey userCredentials = (SSHUserPrivateKey) this.credentials;
                Secret secretPassPhrase = userCredentials.getPassphrase();
                String passphrase = secretPassPhrase == null ? null : secretPassPhrase.getPlainText();

                PrivateKeyParser parser = new PrivateKeyParser();
                for (String key : userCredentials.getPrivateKeys()) {
                    Collection<KeyPair> keys = parser.parseKey(key, passphrase);
                    keys.forEach(it -> session.addPublicKeyIdentity(it));
                }
            }
        } catch (GeneralSecurityException | URISyntaxException e) {
            throw new IOException(e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public String getHostname() {
        return this.host;
    }

    /** {@inheritDoc} */
    @Override
    public int getPort() {
        return this.port;
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        if (session != null) {
            try {
                session.close();
            } catch (IOException e) {
                // NOOP
            }
        }
        if (client != null) {
            client.stop();
        }
        client = null;
        session = null;
    }

    /** {@inheritDoc} */
    @Override
    public void copyFile(String remotePath, byte[] bytes, boolean overwrite, boolean checkSameContent)
            throws IOException {
        try (ClientSession session = connect()) {
            DefaultScpClient scp = new DefaultScpClient(session);
            List<PosixFilePermission> permissions = new ArrayList<>();
            // TODO document the permissions the file needs and how to set the umask
            // TODO verify if the file exists and if the content is the same
            // TODO verify if the file is a directory
            permissions.add(PosixFilePermission.OWNER_WRITE);
            scp.upload(bytes, remotePath, permissions, null);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setServerHostKeyAlgorithms(String[] algorithms) {}

    /** {@inheritDoc} */
    @Override
    public void setTCPNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    /** {@inheritDoc} */
    @Override
    public ClientSession connect() throws IOException {
        initClient();
        if (isSession() == false) {
            for (int i = 0; i <= maxNumRetries; i++) {
                try {
                    return connectAndAuthenticate();
                } catch (Exception ex) {
                    String message = getExMessage(ex);
                    if (maxNumRetries - i > 0) {
                        println(
                                stderr,
                                "SSH Connection failed with IOException: \""
                                        + message
                                        + "\", retrying in "
                                        + retryWaitTime
                                        + " seconds."
                                        + " There are "
                                        + (maxNumRetries - i)
                                        + " more retries left.");
                    }
                }
                waitToRetry();
            }
            throw new IOException("Max number or reties reached.");
        }
        return session;
    }

    /**
     * @return True is the session is authenticated and open.
     */
    private boolean isSession() {
        return session != null && session.isAuthenticated() && session.isOpen();
    }

    /**
     * Connets to the SSH service configured and authenticate
     *
     * @return Returns a ClientSession connected and authenticated.
     * @throws IOException in case of error.
     */
    private ClientSession connectAndAuthenticate() throws IOException {
        // TODO reuse the authentiction implemented at
        // https://github.com/jenkinsci/mina-sshd-api-plugin/blob/main/mina-sshd-api-core/src/main/java/io/jenkins/plugins/mina_sshd_api/core/authenticators/MinaSSHPasswordKeyAuthenticator.java
        ConnectFuture connectionFuture = client.connect(this.credentials.getUsername(), this.host, this.port);
        connectionFuture.verify(this.timeoutMillis);
        session = connectionFuture.getSession();
        addAuthentication();
        AuthFuture auth = session.auth();
        auth.verify(this.timeoutMillis);
        return session;
    }

    /** Initialize the SSH client. It reuses the client if it exists. */
    private void initClient() {
        if (client == null) {
            client = SshClient.setUpDefaultClient();
            CoreModuleProperties.WINDOW_SIZE.set(client, WINDOW_SIZE);
            CoreModuleProperties.TCP_NODELAY.set(client, tcpNoDelay);
            CoreModuleProperties.HEARTBEAT_REQUEST.set(client, "keepalive@jenkins.io");
            CoreModuleProperties.HEARTBEAT_INTERVAL.set(client, Duration.ofSeconds(HEARTBEAT_INTERVAL));
            CoreModuleProperties.HEARTBEAT_NO_REPLY_MAX.set(client, HEARTBEAT_MAX_RETRY);
            CoreModuleProperties.IDLE_TIMEOUT.set(client, Duration.ofMinutes(IDLE_SESSION_TIMEOUT));
        }
        if (client.isStarted() == false) {
            client.start();
        }
    }

    /**
     * Sleep retryWaitTime seconds.
     *
     * @throws IOException in case of error.
     */
    private void waitToRetry() throws IOException {
        try {
            Thread.sleep(TimeUnit.SECONDS.toMillis(retryWaitTime));
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    /**
     * Parse an exception to print the cause or the error message in the error output and return the
     * message.
     *
     * @param ex Exception to parse.
     * @return
     */
    private String getExMessage(Exception ex) {
        String message = "unknown error";
        Throwable cause = ex.getCause();
        if (cause != null) {
            message = cause.getMessage();
            println(stderr, message);
        } else if (ex.getMessage() != null) {
            message = ex.getMessage();
            println(stderr, message);
        }
        return message;
    }

    /**
     * Prints a message in the output passed as parameter.
     *
     * @param out Output to use.
     * @param message Message to write.
     */
    private void println(OutputStream out, String message) {
        if (out instanceof PrintStream) {
            ((PrintStream) out).println(message);
        } else {
            try {
                out.write((message + "\n").getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                // NOOP
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setServerHostKeyVerifier(ServerHostKeyVerifier verifier) {
        this.hostKeyVerifier = verifier;
    }

    /** {@inheritDoc} */
    @Override
    public void setTimeout(long timeout) {
        this.timeoutMillis = timeout;
    }

    /** {@inheritDoc} */
    @Override
    public void setCredentials(StandardUsernameCredentials credentials) {
        this.credentials = credentials;
    }

    /** {@inheritDoc} */
    @Override
    public void setRetryWaitTime(int time) {
        this.retryWaitTime = time;
    }

    /** {@inheritDoc} */
    @Override
    public void setRetries(int retries) {
        this.maxNumRetries = retries;
    }

    /** {@inheritDoc} */
    @Override
    public void setWorkingDirectory(String path) {
        this.workingDirectory = path;
    }

    /** {@inheritDoc} */
    @Override
    public void setStdErr(OutputStream stderr) {
        this.stderr = stderr;
    }

    /** {@inheritDoc} */
    @Override
    public void setStdOut(OutputStream stdout) {
        this.stdout = stdout;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isOpen() {
        return isSession() && client != null && client.isOpen();
    }

    /**
     * Returns the host key verifier used for the connection.
     *
     * @return The host key verifier.
     */
    public ServerHostKeyVerifier getHostKeyVerifier() {
        return hostKeyVerifier;
    }

    /**
     * Returns the working directory used for the connection.
     *
     * @return The working directory.
     */
    public String getWorkingDirectory() {
        return workingDirectory;
    }
}
