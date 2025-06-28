/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: MIT
 */
package io.jenkins.plugins.sshbuildagents.ssh;

import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import java.io.IOException;
import java.io.OutputStream;
import org.apache.sshd.client.session.ClientSession;

/**
 * Interface to manage an SSH connection.
 *
 */
public interface Connection extends AutoCloseable {
    /**
     * Execute a command and return the error code returned when it finish.
     *
     * @param command Command to execute.
     * @return The error code returned by the command.
     * @throws IOException in case of error.
     */
    int execCommand(String command) throws IOException;

    /**
     * Create a {@link #shellChannel()} to execute non-interactive commands.
     *
     * @return Return a {@link #shellChannel()}
     * @throws IOException
     */
    ShellChannel shellChannel() throws IOException;

    /**
     * @return Return the host configured to connect by SSH.
     */
    String getHostname();

    /**
     * @return Return the port configured to connect by SSH.
     */
    int getPort();

    /** Close the connections and resources associated. */
    void close();

    /**
     * Copy a file to the host by SCP. It does not create folders, so the folders of the path should
     * exist.
     *
     * @param remoteFile Full path to the remote file.
     * @param bytes Array of bytes with the data to write.
     * @param overwrite True to overwrite exit files.
     * @param checkSameContent True to check the md5 of the file before write it and do not update the
     *     lie if it is the same.
     * @throws IOException
     */
    void copyFile(String remoteFile, byte[] bytes, boolean overwrite, boolean checkSameContent) throws IOException;

    /**
     * Set server host key Algorithms.
     *
     * @param algorithms Array of Host Key Algorithms.
     */
    void setServerHostKeyAlgorithms(String[] algorithms);

    /**
     * Set the TCP_NODELAY flag on connections.
     *
     * @param tcpNoDelay True to set TCP_NODELAY.
     */
    void setTCPNoDelay(boolean tcpNoDelay);

    /**
     * Establishes an SSH connection with the configuration set in the class.
     *
     * @return Return a {@link ClientSession} to interact with the SSH connection.
     * @throws IOException
     */
    ClientSession connect() throws IOException;

    /**
     * Set Server host verifier.
     *
     * @param verifier The Server host verifier to use.
     */
    void setServerHostKeyVerifier(ServerHostKeyVerifier verifier);

    /**
     * Set the connection timeout.
     *
     * @param timeout Timeout in milliseconds.
     */
    void setTimeout(long timeout);

    /**
     * Set the credential to use to authenticate in the SSH service.
     *
     * @param credentials Credentials used to authenticate.
     */
    void setCredentials(StandardUsernameCredentials credentials);

    /**
     * Set the time to wait between retries.
     *
     * @param time Time to wait in seconds.
     */
    void setRetryWaitTime(int time);

    /**
     * Set the number of times we will retry the SSH connection.
     *
     * @param retries Number of retries.
     */
    void setRetries(int retries);

    /**
     * Set the absolute path to the working directory.
     *
     * @param path absolute path to the working directory.
     */
    void setWorkingDirectory(String path);

    /**
     * Set the standard error output.
     *
     * @param stderr Value of the new standard error output.
     */
    void setStdErr(OutputStream stderr);

    /**
     * Set the standard output.
     *
     * @param stdout Value of the new standard output.
     */
    void setStdOut(OutputStream stdout);

    /**
     * Check if the connection is open.
     *
     * @return True if the connection is open, false otherwise.
     */
    boolean isOpen();
}
