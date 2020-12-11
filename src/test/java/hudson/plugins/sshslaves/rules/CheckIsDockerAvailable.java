package hudson.plugins.sshslaves.rules;

import org.junit.rules.ExternalResource;
import org.testcontainers.DockerClientFactory;

/**
 * Rule to check if Docker is available.
 *
 * @author Kuisathaverat
 */
public class CheckIsDockerAvailable extends ExternalResource {
  @Override
  protected void before() throws Throwable {
    org.junit.Assume.assumeTrue(isDockerAvailable());
  }

  boolean isDockerAvailable() {
    try {
      DockerClientFactory.instance().client();
      return true;
    } catch (Throwable ex) {
      return false;
    }
  }
}
