package hudson.plugins.sshslaves.rules;

import org.apache.commons.lang.SystemUtils;
import org.junit.Assume;
import org.junit.rules.ExternalResource;

/**
 * Rule to check the Operating system where the test run.
 *
 * @author Kuisathaverat
 */
public class CheckIsLinuxOrMac extends ExternalResource {
  @Override
  protected void before() throws Throwable {
    Assume.assumeTrue(SystemUtils.IS_OS_MAC || SystemUtils.IS_OS_LINUX);
  }
}
