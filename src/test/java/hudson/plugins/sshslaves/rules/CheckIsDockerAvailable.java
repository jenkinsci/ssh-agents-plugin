package hudson.plugins.sshslaves.rules;

import org.apache.commons.lang.SystemUtils;
import org.junit.rules.ExternalResource;
import org.testcontainers.DockerClientFactory;

import java.io.IOException;

/**
 * Rule to check if Docker is available.
 *
 * @author Kuisathaverat
 */
public class CheckIsDockerAvailable extends ExternalResource {
  @Override
  protected void before() {
    org.junit.Assume.assumeTrue(isDockerAvailable());
  }

  boolean isDockerAvailable() {
    int exitCode = 0;
    try {
      ProcessBuilder builder = new ProcessBuilder();
      if (SystemUtils.IS_OS_WINDOWS) {
        builder.command("cmd.exe", "/c", "docker --version");
      } else {
        builder.command("sh", "-c", "docker --version");
      }
      Process process = builder.start();
      exitCode = process.waitFor();
    } catch (InterruptedException|IOException e) {
      exitCode = 0;
    }
    return exitCode == 0;
  }
}
