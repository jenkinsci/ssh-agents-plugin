package hudson.plugins.sshslaves;

import hudson.model.Node.Mode;
import hudson.model.Slave;
import hudson.slaves.DumbSlave;
import hudson.slaves.RetentionStrategy;
import org.jvnet.hudson.test.HudsonTestCase;

import java.util.Collections;

/**
 * @author Kohsuke Kawaguchi
 */
public class SSHLauncher2Test extends HudsonTestCase {
    public void testConfigurationRoundtrip() throws Exception {
        SSHLauncher launcher = new SSHLauncher("localhost", 123, "test", "pass", "xyz", "def");
        DumbSlave slave = new DumbSlave("slave", "dummy",
                createTmpDir().getPath(), "1", Mode.NORMAL, "",
                launcher, RetentionStrategy.NOOP, Collections.EMPTY_LIST);
        hudson.addNode(slave);

        submit(createWebClient().getPage(slave,"configure").getFormByName("config"));
        Slave n = (Slave)hudson.getNode("slave");

        assertNotSame(n,slave);
        assertNotSame(n.getLauncher(),launcher);
        assertEqualDataBoundBeans(n.getLauncher(),launcher);
    }

}
