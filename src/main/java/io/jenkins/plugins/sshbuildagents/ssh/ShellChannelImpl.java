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
package io.jenkins.plugins.sshbuildagents.ssh;

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
 * Implements {@link ShellChannel} using the Apache Mina SSHD library https://github.com/apache/mina-sshd
 * @author Ivan Fernandez Calvo
 */
public class ShellChannelImpl implements ShellChannel {
  private final ClientSession session;
  private ChannelSession channel;

  private OutputStream out = new PipedOutputStream();
  private OutputStream invertedIn = new PipedOutputStream();
  private InputStream in = new PipedInputStream((PipedOutputStream)invertedIn);
  private InputStream invertedOut = new PipedInputStream((PipedOutputStream)out);

  public ShellChannelImpl(ClientSession session) throws IOException {
    this.session = session;
  }

  @Override
  public void execCommand(String cmd) throws IOException {
    this.channel = session.createExecChannel(cmd + "\n");
    channel.setOut(out);
    channel.setIn(in);
    channel.open().verify(5L, TimeUnit.SECONDS);
    channel.waitFor(Collections.singleton(ClientChannelEvent.CLOSED), 10000);
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
  public void close() throws IOException {
    channel.close();
    session.close();
  }
}
