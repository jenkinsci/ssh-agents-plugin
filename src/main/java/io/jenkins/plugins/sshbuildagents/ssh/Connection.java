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
     * Execute a command and return the exit code returned when it finish.
     *
     * @param command Command to execute.
     * @return The exit code of the command (if the command ran).
     * @throws IOException in case of an error launching the command.
     */
    int execCommand(String command) throws IOException;

    /**
     * Create a {@link ShellChannel} to execute non-interactive commands.
     *
     * @return Return a {@link ShellChannel}
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

    /**
     * Copy a file to the host by SCP. It does not create folders, so the folders of the path must
     * exist prior to calling this.
     * FIXME The remote file should be relative to the working directory.
     * @param remoteFile Full path to the remote file.
     * @param data Array of bytes with the data to write.
     * @param overwrite @{code true} to overwrite the file if it already exists.  If @{false} and the file exists an @{code IOException} will be thrown.
     * @param checkSameContent if true will calculate and compare the checksum of the remote file and data and if identical will skip writing the file.
     * @throws IOException
     */
    void copyFile(String remoteFile, byte[] data, boolean overwrite, boolean checkSameContent) throws IOException;

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
