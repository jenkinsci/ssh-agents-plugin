package hudson.plugins.sshslaves;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.trilead.ssh2.Connection;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.JDK;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.SlaveComputer;
import hudson.tools.ToolLocationNodeProperty;
import hudson.tools.ToolLocationNodeProperty.ToolLocation;
import hudson.util.DescribableList;
import jenkins.model.Jenkins;

/**
 * Class to try to guess where is java.
 */
@Extension
public class DefaultJavaProvider extends JavaProvider {

    @Override
    public List<String> getJavas(SlaveComputer computer, TaskListener listener, Connection connection) {
        List<String> javas = new ArrayList<String>(
                Arrays.asList("java",
                              "/usr/bin/java",
                              "/usr/java/default/bin/java",
                              "/usr/java/latest/bin/java",
                              "/usr/local/bin/java",
                              "/usr/local/java/bin/java")); // this is where we attempt to auto-install

        String workingDirectory = SSHLauncher.getWorkingDirectory(computer);
        if (workingDirectory != null) {
            javas.add(workingDirectory + "/jdk/bin/java");
        }

        final Node node = computer.getNode();
        DescribableList<NodeProperty<?>, NodePropertyDescriptor> list = node != null ? node.getNodeProperties() : null;
        if (list != null) {
            Descriptor jdk = Jenkins.getActiveInstance().getDescriptorByType(JDK.DescriptorImpl.class);
            for (NodeProperty prop : list) {
                if (prop instanceof EnvironmentVariablesNodeProperty) {
                    EnvVars env = ((EnvironmentVariablesNodeProperty) prop).getEnvVars();
                    if (env != null && env.containsKey("JAVA_HOME")) {
                        javas.add(env.get("JAVA_HOME") + "/bin/java");
                    }
                } else if (prop instanceof ToolLocationNodeProperty) {
                    for (ToolLocation tool : ((ToolLocationNodeProperty) prop).getLocations()) {
                        if (tool.getType() == jdk) {
                            javas.add(tool.getHome() + "/bin/java");
                        }
                    }
                }
            }
        }
        return javas;
    }
}
