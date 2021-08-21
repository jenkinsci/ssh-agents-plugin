package io.jenkins.plugins.sshbuildagents.ssh;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Interface to manage non-interactive sessions.
 * @author Ivan Fernandez Calvo
 */
public interface ShellChannel {
  /**
   * Executed a command in a non-interactive session and exit, it does not wait for the result.
   * @param cmd
   * @throws IOException
   */
  void execCommand(String cmd) throws IOException;

  /**
   *
   * @return The standard output of the process launched in a InputStream for reading.
   */
  InputStream getInvertedStdout();

  /**
   *
   * @return The standard input of the process launched in a OutputStream for writting.
   */
  OutputStream getInvertedStdin();

  /**
   * Closses the channel and resources associated.
   * @throws IOException in case of error.
   */
  void close() throws IOException;

}
