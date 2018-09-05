package hudson.plugins.sshslaves;

import java.io.BufferedReader;
import java.io.IOException;

public interface SSHProvider extends SSHLauncherConfig{
    void close();

    void execCommand(String cmd) throws IOException;

    BufferedReader exec(String cmd) throws IOException, InterruptedException;

    void openConnection() throws IOException, InterruptedException;

    void copyAgentJar(String workingDirectory) throws IOException, InterruptedException;

    void startAgent(String java, String workingDirectory) throws IOException;

    void cleanupConnection();
}
