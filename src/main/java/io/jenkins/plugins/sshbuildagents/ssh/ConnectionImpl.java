package io.jenkins.plugins.sshbuildagents.ssh;

import com.cloudbees.plugins.credentials.Credentials;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import io.jenkins.cli.shaded.org.apache.sshd.client.session.ClientSession;
import io.jenkins.cli.shaded.org.apache.sshd.common.util.io.NoCloseInputStream;

public class ConnectionImpl implements Connection{
  PrintStream stdout = System.out;
  PrintStream stderr = System.err;
  private final String host;
  private final int port;

  public ConnectionImpl(String host, int port) {
    this.host = host;
    this.port = port;
  }

  @Override
  public int exec(String command, OutputStream stdout) {
    ClientSession session = null;
    try (BufferedReader stdin = new BufferedReader(new InputStreamReader(new NoCloseInputStream(System.in),
                                                                         Charset.defaultCharset()))) {
      session = client.connect(entry, null, null)
                      .verify(org.apache.sshd.cli.client.CliClientModuleProperties.CONECT_TIMEOUT.getRequired(client))
                      .getSession();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return 0;
  }

  @Override
  public String getHostname() {
    return null;
  }

  @Override
  public int getPort() {
    return 0;
  }

  @Override
  public void close() {

  }

  @Override
  public void copyFile(String fileName, byte[] bytes, boolean overwrite, boolean checkSameContent) {

  }

  @Override
  public void setServerHostKeyAlgorithms(String[] preferredKeyAlgorithms) {

  }

  @Override
  public Session openSession() {
    return null;
  }

  @Override
  public void setTCPNoDelay(boolean tcpNoDelay) {

  }

  @Override
  public void connect(ServerHostKeyVerifier serverHostKeyVerifier, int connectionTimeoutmillis, Credentials credentials) {

  }
}
