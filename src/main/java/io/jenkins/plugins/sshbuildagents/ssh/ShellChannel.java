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

/**
 * Interface to manage non-interactive sessions.
 *
 * @author Ivan Fernandez Calvo
 */
public interface ShellChannel extends AutoCloseable {
    /**
     * Executed a command in a non-interactive session and exit, it does not wait for the result.
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
