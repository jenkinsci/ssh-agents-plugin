package hudson.plugins.sshslaves.rules;

import org.junit.rules.ExternalResource;
import org.testcontainers.DockerClientFactory;

import static org.junit.Assume.assumeTrue;

/**
 * Rule to check if Docker is available.
 *
 * @author Kuisathaverat
 */
public class CheckIsDockerAvailable extends ExternalResource {
  @Override
  protected void before() {
    assumeTrue(DockerClientFactory.instance().isDockerAvailable());
  }
}
