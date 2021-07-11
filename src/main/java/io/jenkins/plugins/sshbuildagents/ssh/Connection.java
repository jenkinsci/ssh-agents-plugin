package io.jenkins.plugins.sshbuildagents.ssh;

import com.cloudbees.plugins.credentials.Credentials;
import hudson.plugins.sshslaves.SSHLauncher;

import java.io.OutputStream;

public interface Connection {
  int exec(String command, OutputStream stdout);
  String getHostname();
  int getPort();
  void close();

  void copyFile(String fileName, byte[] bytes, boolean overwrite, boolean checkSameContent);

  void setServerHostKeyAlgorithms(String[] preferredKeyAlgorithms);

  Session openSession();

  void setTCPNoDelay(boolean tcpNoDelay);

  void connect(ServerHostKeyVerifier serverHostKeyVerifier, int connectionTimeoutmillis, Credentials credentials);
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
