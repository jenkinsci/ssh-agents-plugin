/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: MIT
 */
package io.jenkins.plugins.sshbuildagents.ssh.mina;

import io.jenkins.plugins.sshbuildagents.ssh.ShellChannel;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.apache.sshd.client.channel.ChannelSession;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;

/**
 * Implements {@link ShellChannel} using the Apache Mina SSHD library
 * https://github.com/apache/mina-sshd
 *
 */
public class ShellChannelImpl implements ShellChannel {

    /** Time between session heartbeat probes. */
    public static final int OPERATION_TIMEOUT = 30000;

    /** SSH Client session. */
    private final ClientSession session;

    /** Shell channel to execute the process. */
    private ChannelSession channel;

    /** Standard output of the channel. the process output is write in it. */
    private OutputStream out = new PipedOutputStream();

    /** Output stream to allow writing in the standard input of the process from outside the class. */
    private OutputStream invertedIn = new PipedOutputStream();

    /** Standard input of the channel. This is sent to the standard input of the process launched. */
    private InputStream in = new PipedInputStream((PipedOutputStream) invertedIn);

    /** Input stream tar allows to read the standard out of the process from outside the class. */
    private InputStream invertedOut = new PipedInputStream((PipedOutputStream) out);

    /** Last exception. */
    private Throwable lastError;

    /** last command received. */
    private String lastHint;

    /**
     * Create a Shell channel for a process execution.
     *
     * @param session SSH session.
     * @throws IOException in case of error.
     */
    public ShellChannelImpl(ClientSession session) throws IOException {
        this.session = session;
    }

    /**
     * Executes a command in the shell.
     *
     * @param cmd Command to execute.
     * @throws IOException in case of error. This method will block until the command finishes
     *     executing.
     */
    @Override
    public void execCommand(String cmd) throws IOException {
        this.channel = session.createExecChannel(cmd + "\n");
        this.lastHint = cmd;
        this.lastError = null;
        channel.setOut(out);
        channel.setIn(in);
        channel.open().verify(OPERATION_TIMEOUT, TimeUnit.MILLISECONDS);
        channel.waitFor(Collections.singleton(ClientChannelEvent.CLOSED), OPERATION_TIMEOUT);
        if (channel.getExitStatus() != null && channel.getExitStatus() != 0) {
            this.lastError = new IOException("Command failed with exit status " + channel.getExitStatus());
        }
    }

    /** Returns the standard output stream of the channel. */
    @Override
    public InputStream getInvertedStdout() {
        return invertedOut;
    }

    /** Returns the standard input stream of the channel. */
    @Override
    public OutputStream getInvertedStdin() {
        return invertedIn;
    }

    /** Returns the standard error stream of the channel. */
    @Override
    public Throwable getLastError() {
        return lastError;
    }

    /** Returns the last command executed. */
    @Override
    public String getLastHint() {
        return lastHint;
    }

    /** Closes the channel. */
    @Override
    public void close() throws IOException {
        channel.close();
        channel = null;
    }
}
