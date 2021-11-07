/*
 * The MIT License
 *
 * Copyright (c) 2016, Michael Clarke
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
package io.jenkins.plugins.sshbuildagents.ssh.mina;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import io.jenkins.plugins.sshbuildagents.ssh.ShellChannel;
import org.apache.sshd.client.channel.ChannelSession;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.channel.Channel;
import org.apache.sshd.common.channel.ChannelListener;
import org.apache.sshd.common.session.ReservedSessionMessagesHandler;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionHeartbeatController;
import org.apache.sshd.common.util.buffer.Buffer;

/**
 * Implements {@link ShellChannel} using the Apache Mina SSHD library https://github.com/apache/mina-sshd
 * @author Ivan Fernandez Calvo
 */
public class ShellChannelImpl implements ShellChannel {

  /**
   * Time between session heartbeat probes.
   */
  public static final int SESSION_HEARTBEAT = 20000;
  public static final int OPERATION_TIMEOUT = 10000;

  /**
   * Enum to represent the channel status.
   */
  public enum Status {
    INIZIALIZED,
    OPEN,
    CLOSED,
    FAILURE
  }

  /**
   * SSH Client session.
   */
  private final ClientSession session;
  /**
   * Shell channel to execute the process.
   */
  private ChannelSession channel;

  /**
   * Standard output of the channel.
   * the process output is write in it.
   */
  private OutputStream out = new PipedOutputStream();
  /**
   * Output stream to allow writing in the standard input of the process from outside the class.
   */
  private OutputStream invertedIn = new PipedOutputStream();
  /**
   * Standard input of the channel.
   * This is sent to the standard input of the process launched.
   */
  private InputStream in = new PipedInputStream((PipedOutputStream)invertedIn);
  /**
   * Input stream tar allows to read the standard out of the process from outside the class.
   */
  private InputStream invertedOut = new PipedInputStream((PipedOutputStream)out);

  /**
   * Current status of the channel.
   */
  private Status status;
  /**
   * Last exception.
   */
  private Throwable lastError;
  /**
   * last command received.
   */
  private String lastHint;
  /**
   * Listener to report the status of the channel.
   */
  private ChannelListener channelListener = new ChannelListener() {
    @Override
    public void channelInitialized(Channel channel) {
      status = Status.INIZIALIZED;
    }

    @Override
    public void channelOpenSuccess(Channel channel) {
      status = Status.OPEN;
    }

    @Override
    public void channelOpenFailure(Channel channel, Throwable reason) {
      status = Status.FAILURE;
      lastError = reason;
    }

    @Override
    public void channelStateChanged(Channel channel, String hint) {
      lastHint = hint;
    }

    @Override
    public void channelClosed(Channel channel, Throwable reason) {
      status = Status.CLOSED;
      lastError = reason;
    }
  };

  /**
   * Create a Shell channel for a process execution.
   * @param session SSH session.
   * @throws IOException in case of error.
   */
  public ShellChannelImpl(ClientSession session) throws IOException {
    this.session = session;
  }

  @Override
  public void execCommand(String cmd) throws IOException {
    this.channel = session.createExecChannel(cmd + "\n");
    session.setSessionHeartbeat(SessionHeartbeatController.HeartbeatType.IGNORE, Duration.ofMillis(SESSION_HEARTBEAT));
    channel.setOut(out);
    channel.setIn(in);
    channel.open().verify(OPERATION_TIMEOUT, TimeUnit.MILLISECONDS);
    channel.addChannelListener(channelListener);
    channel.waitFor(Collections.singleton(ClientChannelEvent.CLOSED), OPERATION_TIMEOUT);
  }

  @Override
  public InputStream getInvertedStdout() {
    return invertedOut;
  }

  @Override
  public OutputStream getInvertedStdin() {
    return invertedIn;
  }

  @Override
  public Status getStatus() {
    return status;
  }

  @Override
  public Throwable getLastError() {
    return lastError;
  }

  @Override
  public String getLastHint() {
    return lastHint;
  }

  @Override
  public void close() throws IOException {
    channel.close();
    channel = null;
  }
}
