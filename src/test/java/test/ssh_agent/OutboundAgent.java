/*
 * The MIT License
 *
 * Copyright 2025 CloudBees, Inc.
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

package test.ssh_agent;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.Functions;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.slaves.DumbSlave;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.jvnet.hudson.test.JenkinsRule;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

/**
 * Test utility to create an outbound agent.
 * Will use Docker when it is available (Testcontainers must be in your plugin classpath),
 * which is preferable as it ensures that the process and filesystem namespace for the agent
 * is distinct from that of the controller.
 * Otherwise it falls back to running an agent process locally.
 */
public final class OutboundAgent implements AutoCloseable {

    private String image = "jenkins/ssh-agent:latest-jdk21";

    private SSHAgentContainer container;

    public OutboundAgent() {}

    /**
     * Overrides the container image, by default {@code jenkins/ssh-agent} (latest).
     */
    public OutboundAgent withImage(String image) {
        this.image = image;
        return this;
    }

    private static final class SSHAgentContainer extends GenericContainer<SSHAgentContainer> {
        final String privateKey;

        SSHAgentContainer(String image) {
            super(image);
            try {
                var kp = KeyUtils.generateKeyPair(KeyPairProvider.SSH_RSA, 2048);
                var kprw = new OpenSSHKeyPairResourceWriter();
                var baos = new ByteArrayOutputStream();
                kprw.writePublicKey(kp, null, baos);
                var pub = baos.toString(StandardCharsets.US_ASCII);
                baos.reset();
                kprw.writePrivateKey(kp, null, null, baos);
                privateKey = baos.toString(StandardCharsets.US_ASCII);
                withEnv("JENKINS_AGENT_SSH_PUBKEY", pub);
                withExposedPorts(22);
            } catch (Exception x) {
                throw new AssertionError(x);
            }
        }
    }

    /**
     * Start the container, if Docker is available.
     * @return Docker connection details, or null if running locally; pass to {@link #createAgent}
     */
    public ConnectionDetails start() throws Exception {
        if (!Functions.isWindows() && DockerClientFactory.instance().isDockerAvailable()) {
            container = new SSHAgentContainer(image);
            container.start();
            return new ConnectionDetails(container.getHost(), container.getMappedPort(22), container.privateKey);
        } else {
            return null;
        }
    }

    /**
     * Treat as opaque between {@link #start} and {@link #createAgent}.
     */
    public record ConnectionDetails(String host, int port, String privateKey) implements Serializable {}

    /**
     * Create an agent.
     * @param rule this should run in the controller’s’ JVM, unlike {@link #start}
     * @param name agent name
     * @param connectionDetails connection details, or null to run a local agent
     * @see JenkinsRule#waitOnline
     */
    public static void createAgent(JenkinsRule rule, String name, ConnectionDetails connectionDetails)
            throws Exception {
        if (connectionDetails != null) {
            var creds = new BasicSSHUserPrivateKey(
                    CredentialsScope.GLOBAL,
                    null,
                    "jenkins",
                    new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(connectionDetails.privateKey),
                    null,
                    null);
            CredentialsProvider.lookupStores(rule.jenkins).iterator().next().addCredentials(Domain.global(), creds);
            rule.jenkins.addNode(new DumbSlave(
                    name,
                    "/home/jenkins/agent",
                    new SSHLauncher(connectionDetails.host, connectionDetails.port, creds.getId())));
        } else {
            rule.createSlave(name, null, null);
        }
    }

    @Override
    public void close() throws Exception {
        if (container != null) {
            container.close();
        }
    }
}
