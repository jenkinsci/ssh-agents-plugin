/*
 * The MIT License
 *
 * Copyright (c) 2004-, all the contributors
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
package hudson.plugins.sshuserslaves;

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
import hudson.slaves.SlaveComputer;
import hudson.tools.ToolLocationNodeProperty;
import hudson.tools.ToolLocationNodeProperty.ToolLocation;
import jenkins.model.Jenkins;

/**
 * Class to try to guess where is java.
 * This is the list of places where it will try to find java:
 *  <p><ul>
 *      <li>Agent working directory - WORKING_DIRECTORY/jdk/bin/java
 *      <li>JAVA_HOME environment variable - JAVA_HOME/bin/java
 *      <li>JDK tools configured on Jenkins - JDK_TOOLS_LOCATIONS/bin/java
 *      <li>PATH
 *      <li>"/usr/bin/java"
 *      <li>"/usr/java/default/bin/java"
 *      <li>"/usr/java/latest/bin/java"
 *      <li>"/usr/local/bin/java"
 *      <li>"/usr/local/java/bin/java
 *  </ul><p>
 *
 */
@Extension
public class DefaultJavaProvider extends JavaProvider {

    public static final String JAVA_HOME = "JAVA_HOME";
    public static final String BIN_JAVA = "/bin/java";
    public static final String JDK_BIN_JAVA = "/jdk/bin/java";

    @Override
    public List<String> getJavas(SlaveComputer computer, TaskListener listener, Connection connection) {
        List<String> javas = new ArrayList<>();

        String workingDirectory = SSHLauncher.getWorkingDirectory(computer);
        if (workingDirectory != null) {
            javas.add(workingDirectory + JDK_BIN_JAVA);
        }

        final Node node = computer.getNode();
        javas.addAll(lookForJavaHome(node));
        javas.addAll(lookForTools(node));
        javas.addAll(Arrays.asList("java",
                                   "/usr/bin/java",
                                   "/usr/java/default/bin/java",
                                   "/usr/java/latest/bin/java",
                                   "/usr/local/bin/java",
                                   "/usr/local/java/bin/java"));
        return javas;
    }

    private List<String> lookForJavaHome(Node node) {
        List<String> ret = new ArrayList<>();
        if(node != null && node.getNodeProperties() != null){
            for (NodeProperty property : node.getNodeProperties()){
                if(property instanceof EnvironmentVariablesNodeProperty){
                    EnvVars env = ((EnvironmentVariablesNodeProperty) property).getEnvVars();
                    if (env != null && env.containsKey(JAVA_HOME)) {
                        ret.add(env.get(JAVA_HOME) + BIN_JAVA);
                    }
                }
            }
        }
        return ret;
    }

    private List<String> lookForTools(Node node) {
        List<String> ret = new ArrayList<>();
        Descriptor jdk = Jenkins.getInstance().getDescriptorByType(JDK.DescriptorImpl.class);
        if(node != null && node.getNodeProperties() != null){
            for (NodeProperty property : node.getNodeProperties()){
                if (property instanceof ToolLocationNodeProperty) {
                    for (ToolLocation tool : ((ToolLocationNodeProperty) property).getLocations()) {
                        if (tool.getType() == jdk) {
                            ret.add(tool.getHome() + BIN_JAVA);
                        }
                    }
                }
            }
        }
        return ret;
    }
}
