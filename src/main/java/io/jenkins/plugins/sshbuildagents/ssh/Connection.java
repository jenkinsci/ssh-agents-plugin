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
import java.io.OutputStream;
import org.apache.sshd.client.session.ClientSession;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;

/**
 * Interface to manage an SSH connection.
 * @author Ivan Fernandez Calvo
 */
public interface Connection {
  /**
   * Execute a command and return the error code returned when it finish.
   * @param command Command to execute.
   * @return The error code returned by the command.
   * @throws IOException in case of error.
   */
  int execCommand(String command) throws IOException;

  /**
   * Create a {@link #shellChannel()} to execute non-interactive commands.
   * @return Return a {@link #shellChannel()}
   * @throws IOException
   */
  ShellChannel shellChannel() throws IOException;

  /**
   * @return Return the host configured to connect by SSH.
   */
  String getHostname();

  /**
   *
   * @return Return the port configured to connect by SSH.
   */
  int getPort();

  /**
   * Close the connections and resources associated.
   */
  void close();

  /**
   * Copy a file to the host by SCP. It does not create folders, so the folders of the path should exist.
   * @param remoteFile Full path to the remote file.
   * @param bytes Array of bytes with the data to write.
   * @param overwrite True to overwrite exit files.
   * @param checkSameContent True to check the md5 of the file before write it and do not update the lie if it is the
   *                        same.
   * @throws IOException
   */
  void copyFile(String remoteFile, byte[] bytes, boolean overwrite, boolean checkSameContent)
          throws IOException;

  /**
   * Set server host key Algorithms.
   * @param algorithms Array of Host Key Algorithms.
   */
  void setServerHostKeyAlgorithms(String[] algorithms);

  /**
   * Set the TCP_NODELAY flag on connections.
   * @param tcpNoDelay True to set TCP_NODELAY.
   */
  void setTCPNoDelay(boolean tcpNoDelay);

  /**
   * Establishes an SSH connection with the configuration set in the class.
   * @return Return a {@link ClientSession} to interact with the SSH connection.
   * @throws IOException
   *
   * TODO replace the interface returned by a generic interface.
   */
  ClientSession connect() throws IOException;

  /**
   * Set Server host verifier.
   * @param verifier The Server host verifier to use.
   */
  void setServerHostKeyVerifier(ServerHostKeyVerifier verifier);

  /**
   * Set the connection timeout.
   * @param timeout Timeout in milliseconds.
   */
  void setTimeout(long timeout);

  /**
   * Set the credential to use to authenticate in the SSH service.
   * @param credentials Credentials used to authenticate.
   */
  void setCredentials(StandardUsernameCredentials credentials);

  /**
   * Set the time to wait between retries.
   * @param time Time to wait in seconds.
   */
  void setRetryWaitTime(int time);

  /**
   * Set the number of times we will retry the SSH connection.
   * @param retries Number of retries.
   */
  void setRetries(int retries);

  /**
   * Set the absolute path to the working directory.
   * @param path absolute path to the working directory.
   */
  void setWorkingDirectory(String path);

  /**
   * Set the standard error output.
   * @param stderr Value of the new standard error output.
   */
  void setStdErr(OutputStream stderr);

  /**
   * Set the standard output.
   * @param stdout Value of the new standard output.
   */
  void setStdOut(OutputStream stdout);

  /*
  TODO rid of SSHAuthenticator

          if (SSHAuthenticator.newInstance(connection, credentials).authenticate(listener)
                && connection.isAuthenticationComplete()) {
            logger.println(Messages.SSHLauncher_AuthenticationSuccessful(getTimestamp()));
        } else {
            logger.println(Messages.SSHLauncher_AuthenticationFailed(getTimestamp()));
            throw new AbortException(Messages.SSHLauncher_AuthenticationFailedException());
        }
   */

          /*
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

                listener.getLogger().println(Messages.SSHLauncher_CopyingAgentJar(getTimestamp()));
                byte[] agentJar = new Slave.JnlpJar(AGENT_JAR).readFully();

                // If the agent jar already exists see if it needs to be updated
                boolean overwrite = true;
                if (sftpClient.exists(fileName)) {
                    String sourceAgentHash = getMd5Hash(agentJar);
                    String existingAgentHash = getMd5Hash(readInputStreamIntoByteArrayAndClose(sftpClient.read(fileName)));
                    listener.getLogger().println(MessageFormat.format( "Source agent hash is {0}. "
                      + "Installed agent hash is {1}", sourceAgentHash, existingAgentHash));

                    overwrite = !sourceAgentHash.equals(existingAgentHash);
                }

                if (overwrite) {
                    try {
                        // try to delete the file in case the agent we are copying is shorter than the agent
                        // that is already there
                        sftpClient.rm(fileName);
                    } catch (IOException e) {
                        // the file did not exist... so no need to delete it!
                    }

                    try (OutputStream os = sftpClient.writeToFile(fileName)) {
                        os.write(agentJar);
                        listener.getLogger()
                          .println(Messages.SSHLauncher_CopiedXXXBytes(getTimestamp(), agentJar.length));
                    } catch (Error error) {
                        throw error;
                    } catch (Throwable e) {
                        throw new IOException(Messages.SSHLauncher_ErrorCopyingAgentJarTo(fileName), e);
                    }
                }else{
                    listener.getLogger().println("Verified agent jar. No update is necessary.");
                }
            } catch (Error error) {
                throw error;
            } catch (Throwable e) {
                throw new IOException(Messages.SSHLauncher_ErrorCopyingAgentJarInto(workingDirectory), e);
            }
        } catch (IOException e) {
            if (sftpClient == null) {
                e.printStackTrace(listener.error(Messages.SSHLauncher_StartingSCPClient(getTimestamp())));
                // lets try to recover if the agent doesn't have an SFTP service
                copySlaveJarUsingSCP(listener, workingDirectory);
            } else {
                throw e;
            }
        } finally {
            if (sftpClient != null) {
                sftpClient.close();
            }
        }

    / **
     * Method reads a byte array and returns an upper case md5 hash for it.
     *
     * @param bytes
     * @return
     * @throws NoSuchAlgorithmException
     * /
          static String getMd5Hash(byte[] bytes) throws NoSuchAlgorithmException {

            String hash;
            try {
              MessageDigest md = MessageDigest.getInstance("MD5");
              md.update(bytes);
              byte[] digest = md.digest();

              char[] hexCode = "0123456789ABCDEF".toCharArray();
              StringBuilder r = new StringBuilder(digest.length * 2);
              for (byte b : digest) {
                r.append(hexCode[(b >> 4) & 0xF]);
                r.append(hexCode[(b & 0xF)]);
              }

              hash = r.toString().toUpperCase();
            }catch (NoSuchAlgorithmException e){
              throw e;
            }
            return hash;
          }

              / **
     * Method copies the agent jar to the remote system using scp.
     *
     * @param listener         The listener.
     * @param workingDirectory The directory into which the agent jar will be copied.
     *
     * @throws IOException If something goes wrong.
     * @throws InterruptedException If something goes wrong.
     * /
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

              // delete the agent jar as we do with SFTP
              connection.exec("rm " + workingDirectory + SLASH_AGENT_JAR, new NullStream());

              // SCP it to the agent. hudson.Util.ByteArrayOutputStream2 doesn't work for this. It pads the byte array.
              listener.getLogger().println(Messages.SSHLauncher_CopyingAgentJar(getTimestamp()));
              scp.put(new Slave.JnlpJar(AGENT_JAR).readFully(), AGENT_JAR, workingDirectory, "0644");
            } catch (IOException e) {
              throw new IOException(Messages.SSHLauncher_ErrorCopyingAgentJarInto(workingDirectory), e);
            }
          }
        */
}
