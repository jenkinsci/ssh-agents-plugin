/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: MIT
 */
package io.jenkins.plugins.sshbuildagents.ssh.agents;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Node;
import hudson.plugins.sshslaves.verifiers.NonVerifyingKeyVerificationStrategy;
import hudson.slaves.DumbSlave;
import io.jenkins.plugins.sshbuildagents.ssh.mina.SSHApacheMinaLauncher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.SystemUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class to test connections to a remote SSH Agent
 *
 */
@Timeout(value = 10, unit = TimeUnit.MINUTES)
@WithJenkins
@Testcontainers(disabledWithoutDocker = true)
public abstract class AgentConnectionBaseTest {

    public static final String USER = "jenkins";
    public static final String PASSWORD = "password";
    protected static final String AGENT_WORK_DIR = "/home/jenkins";
    protected static final int SSH_PORT = 22;
    protected static final String SSH_SSHD_CONFIG = "ssh/sshd_config";
    protected static final String DOCKERFILE = "Dockerfile";
    protected static final String SSH_AUTHORIZED_KEYS = "ssh/authorized_keys";
    protected static final String AGENTS_RESOURCES_PATH = "/hudson/plugins/sshslaves/agents/";

    protected JenkinsRule j;

    @BeforeEach
    void beforeEach(JenkinsRule j) {
        this.j = j;
        this.j.timeout = 0;
    }

    @BeforeAll
    static void beforeAll() {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        assumeTrue(SystemUtils.IS_OS_MAC || SystemUtils.IS_OS_LINUX);
        assumeTrue(DockerClientFactory.instance().isDockerAvailable());
    }

    @Test
    void connectionTests() throws IOException, InterruptedException, Descriptor.FormException {
        Node node = createPermanentAgent(
                getAgentName(),
                getAgentContainer().getHost(),
                getAgentContainer().getMappedPort(SSH_PORT),
                getAgentSshKeyPath(),
                getAgentSshKeyPassphrase());
        waitForAgentConnected(node);
        assertTrue(isSuccessfullyConnected(node));
    }

    protected abstract String getAgentName();

    protected abstract GenericContainer<?> getAgentContainer();

    protected String getAgentSshKeyPath() {
        return null;
    }

    protected String getAgentSshKeyPassphrase() {
        return "";
    }

    protected static boolean isSuccessfullyConnected(Node node) throws IOException, InterruptedException {
        boolean ret = false;
        int count = 0;
        while (count < 30) {
            Thread.sleep(1000);
            String log = node.toComputer().getLog();
            ret = log.contains("Agent successfully connected and online");
            count++;
        }
        return ret;
    }

    protected void waitForAgentConnected(Node node) throws InterruptedException {
        int count = 0;
        while (!node.toComputer().isOnline() && count < 150) {
            Thread.sleep(1000);
            count++;
        }
        assertTrue(node.toComputer().isOnline());
    }

    protected Node createPermanentAgent(
            String name, String host, int sshPort, String keyResourcePath, String passphrase)
            throws Descriptor.FormException, IOException {
        String credId = "sshCredentialsId";

        if (keyResourcePath != null) {
            createSshKeyCredentials(credId, keyResourcePath, passphrase);
        } else {
            createSshCredentials(credId);
        }

        final SSHApacheMinaLauncher launcher = new SSHApacheMinaLauncher(host, sshPort, credId);
        initLauncher(launcher);
        DumbSlave agent = new DumbSlave(name, AGENT_WORK_DIR, launcher);
        j.jenkins.addNode(agent);
        return j.jenkins.getNode(agent.getNodeName());
    }

    private void initLauncher(SSHApacheMinaLauncher launcher) {
        launcher.setSshHostKeyVerificationStrategy(new NonVerifyingKeyVerificationStrategy());
        launcher.setJvmOptions(" -Dhudson.remoting.Launcher.pingIntervalSec=-1 "
                + "-Dhudson.slaves.ChannelPinger.pingIntervalSeconds=-1 "
                + "-Djava.awt.headless=true ");
        launcher.setSuffixStartAgentCmd(" -loggingConfig /home/jenkins/.ssh/remoting_logger.properties ");
    }

    private void createSshKeyCredentials(String id, String keyResourcePath, String passphrase) throws IOException {
        String privateKey = IOUtils.toString(getClass().getResourceAsStream(keyResourcePath), StandardCharsets.UTF_8);
        BasicSSHUserPrivateKey.DirectEntryPrivateKeySource privateKeySource =
                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(privateKey);
        BasicSSHUserPrivateKey credentials = new BasicSSHUserPrivateKey(
                CredentialsScope.SYSTEM, id, USER, privateKeySource, passphrase, "Private Key ssh credentials");
        SystemCredentialsProvider.getInstance()
                .getDomainCredentialsMap()
                .put(Domain.global(), Collections.singletonList(credentials));
    }

    private static void createSshCredentials(String id) throws FormException {
        StandardUsernameCredentials credentials =
                new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM, id, "", USER, PASSWORD);
        SystemCredentialsProvider.getInstance()
                .getDomainCredentialsMap()
                .put(Domain.global(), Collections.singletonList(credentials));
    }
}
