/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: MIT
 */
package io.jenkins.plugins.sshbuildagents.ssh;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Interface to manage non-interactive sessions.
 *
 */
public interface ShellChannel extends AutoCloseable {
    /**
     * Execute a command in a non-interactive session and return without waiting for the command to complete.
     *
     * @param cmd
     * @throws IOException
     */
    void execCommand(String cmd) throws IOException;

    /**
     * @return The standard output of the process launched in a InputStream for reading.
     */
    InputStream getInvertedStdout();

    /**
     * @return The standard input of the process launched in a OutputStream for writting.
     */
    OutputStream getInvertedStdin();

    /**
     * @return the last error in the channel.
     */
    Throwable getLastError();

    /**
     * @return the last command received in the SSH channel.
     */
    String getLastHint();

    /**
     * Closses the channel and resources associated.
     *
     * @throws IOException in case of error.
     */
    void close() throws IOException;
}
