package io.jenkins.plugins.sshbuildagents.ssh;

import hudson.plugins.sshslaves.SSHLauncher;

import java.io.InputStream;
import java.io.OutputStream;

public interface Session {
  void execCommand(String cmd);

  void pipeStderr(OutputStream stderr);

  InputStream getStdout();

  OutputStream getStdin();

  void close();
}
