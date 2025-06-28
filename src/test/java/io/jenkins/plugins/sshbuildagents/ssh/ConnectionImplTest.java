/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: MIT
 */
package io.jenkins.plugins.sshbuildagents.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Descriptor.FormException;
import io.jenkins.plugins.sshbuildagents.ssh.agents.AgentConnectionBaseTest;
import io.jenkins.plugins.sshbuildagents.ssh.mina.ConnectionImpl;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.util.io.output.NoCloseOutputStream;
import org.apache.sshd.scp.server.ScpCommandFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.pubkey.AcceptAllPublickeyAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.shell.InteractiveProcessShellFactory;
import org.apache.sshd.server.shell.ProcessShellCommandFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

public class ConnectionImplTest {
    private SshServer sshd;

    @TempDir
    public Path tempFolder;

    @BeforeEach
    public void setup() throws IOException {
        Logger.getLogger("org.apache.sshd").setLevel(Level.FINE);
        Logger.getLogger("io.jenkins.plugins.sshbuildagents").setLevel(Level.FINE);
        sshd = SshServer.setUpDefaultServer();
        sshd.setHost("127.0.0.1");
        ScpCommandFactory.Builder cmdFactoryBuilder = new ScpCommandFactory.Builder();
        sshd.setCommandFactory(cmdFactoryBuilder
                .withDelegate(ProcessShellCommandFactory.INSTANCE)
                .build());
        sshd.setShellFactory(InteractiveProcessShellFactory.INSTANCE);
        sshd.setPasswordAuthenticator((username, password, session) ->
                AgentConnectionBaseTest.USER.equals(username) && AgentConnectionBaseTest.PASSWORD.equals(password));
        sshd.setPublickeyAuthenticator(AcceptAllPublickeyAuthenticator.INSTANCE);
        sshd.setPublickeyAuthenticator(AcceptAllPublickeyAuthenticator.INSTANCE);
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());

        sshd.start();
    }

    @AfterEach
    public void tearDown() throws IOException {
        sshd.stop();
    }

    @Test
    public void testRunCommandUserPassword() throws IOException, FormException {
        Connection connection = new ConnectionImpl(sshd.getHost(), sshd.getPort());
        StandardUsernameCredentials credentials = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, "id", "", AgentConnectionBaseTest.USER, AgentConnectionBaseTest.PASSWORD);
        connection.setCredentials(credentials);
        int ret = connection.execCommand("echo FOO");
        connection.close();
        assertEquals(ret, 0);
    }

    @Test
    public void testRunCommandSSHKey() throws IOException {
        Connection connection = new ConnectionImpl(sshd.getHost(), sshd.getPort());
        StandardUsernameCredentials credentials = new FakeSSHKeyCredential();
        connection.setCredentials(credentials);
        int ret = connection.execCommand("echo FOO");
        connection.close();
        assertEquals(ret, 0);
    }

    @Test
    public void testCopyFile() throws IOException, FormException {
        final File tempFile =
                Files.createFile(tempFolder.resolve("tempFile.txt")).toFile();
        try (Connection connection = new ConnectionImpl(sshd.getHost(), sshd.getPort())) {
            StandardUsernameCredentials credentials = new UsernamePasswordCredentialsImpl(
                    CredentialsScope.SYSTEM, "id", "", AgentConnectionBaseTest.USER, AgentConnectionBaseTest.PASSWORD);
            connection.setCredentials(credentials);
            String data = IOUtils.toString(getClass().getResourceAsStream("/fakeAgentJar.txt"), StandardCharsets.UTF_8);
            connection.copyFile(tempFile.getAbsolutePath(), data.getBytes(StandardCharsets.UTF_8), true, true);
            String dataUpload = FileUtils.readFileToString(tempFile, StandardCharsets.UTF_8);
            assertEquals(data, dataUpload);
        }
    }

    @Test
    public void testShellChannel() throws IOException, FormException {
        Logger logger = Logger.getLogger("io.jenkins.plugins.sshbuildagents.ssh.agents");
        try (Connection connection = new ConnectionImpl(sshd.getHost(), sshd.getPort())) {
            StandardUsernameCredentials credentials = new UsernamePasswordCredentialsImpl(
                    CredentialsScope.SYSTEM, "id", "", AgentConnectionBaseTest.USER, AgentConnectionBaseTest.PASSWORD);
            connection.setCredentials(credentials);
            ShellChannel shellChannel = connection.shellChannel();
            shellChannel.execCommand("echo FOO");
            byte[] data = IOUtils.readFully(
                    shellChannel.getInvertedStdout(),
                    shellChannel.getInvertedStdout().available());
            String dataStr = IOUtils.toString(data, "UTF-8");
            logger.info(dataStr);
            assertEquals("FOO", StringUtils.chomp(dataStr));
        }
    }

    @Test
    @DisabledIfEnvironmentVariable(named = "LONG_CONNECTION_TEST", matches = "")
    public void testRunLongConnection() throws IOException, InterruptedException {
        try (Connection connection = new ConnectionImpl(sshd.getHost(), sshd.getPort())) {
            StandardUsernameCredentials credentials = new FakeSSHKeyCredential();
            connection.setCredentials(credentials);
            ShellChannel shellChannel = connection.shellChannel();
            shellChannel.execCommand("sleep 500s");
            for (int i = 0; i < 300; i++) {
                Thread.sleep(1000);
                assertTrue(connection.isOpen());
            }
        }
    }

    @Test
    public void testShellChannel2() throws IOException, FormException {
        Logger logger = Logger.getLogger("io.jenkins.plugins.sshbuildagents.ssh.agents");
        try (Connection connection = new ConnectionImpl(sshd.getHost(), sshd.getPort())) {
            StandardUsernameCredentials credentials = new UsernamePasswordCredentialsImpl(
                    CredentialsScope.SYSTEM, "id", "", AgentConnectionBaseTest.USER, AgentConnectionBaseTest.PASSWORD);
            connection.setCredentials(credentials);
            try (ClientSession session = connection.connect();
                    PipedOutputStream pipedIn = new PipedOutputStream();
                    InputStream inPipe = new PipedInputStream(pipedIn);
                    ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                try (ChannelShell channel = session.createShellChannel()) {
                    channel.setOut(new NoCloseOutputStream(out));
                    channel.setErr(new NoCloseOutputStream(out));
                    channel.setIn(inPipe);
                    channel.open().verify(5L, TimeUnit.SECONDS);
                    pipedIn.write(("echo BAR\n").getBytes(StandardCharsets.UTF_8));
                    pipedIn.flush();
                    channel.waitFor(Collections.singleton(ClientChannelEvent.CLOSED), 10000);
                    logger.info(out.toString("UTF-8"));
                }
            }
        }
    }

    @Test
    public void testClient() throws Exception {
        Logger logger = Logger.getLogger("io.jenkins.plugins.sshbuildagents.ssh.agents");
        try (Connection connection = new ConnectionImpl(sshd.getHost(), sshd.getPort())) {
            StandardUsernameCredentials credentials = new UsernamePasswordCredentialsImpl(
                    CredentialsScope.SYSTEM, "id", "", AgentConnectionBaseTest.USER, AgentConnectionBaseTest.PASSWORD);
            connection.setCredentials(credentials);
            try (ClientSession session = connection.connect();
                    ClientChannel channel = session.createShellChannel();
                    ByteArrayOutputStream sent = new ByteArrayOutputStream();
                    PipedOutputStream pipedIn = new PipedOutputStream();
                    PipedInputStream pipedOut = new PipedInputStream(pipedIn);
                    ByteArrayOutputStream out = new ByteArrayOutputStream()) {

                channel.setIn(pipedOut);
                channel.setOut(out);
                channel.setErr(out);
                channel.open();

                pipedIn.write("touch /tmp/FOO\n".getBytes(StandardCharsets.UTF_8));
                pipedIn.flush();

                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 1000; i++) {
                    sb.append("echo FOO\n");
                }
                sb.append('\n');
                pipedIn.write(sb.toString().getBytes(StandardCharsets.UTF_8));

                pipedIn.write("exit\n".getBytes(StandardCharsets.UTF_8));
                pipedIn.flush();
                logger.info(out.toString());
                channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), 10000);

                channel.close(false);
                connection.close();

            } finally {
                connection.close();
            }
        }
    }
}
