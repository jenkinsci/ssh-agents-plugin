/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: MIT
 */
package io.jenkins.plugins.sshbuildagents.ssh;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BaseSSHUser;
import com.cloudbees.plugins.credentials.CredentialsScope;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.util.Secret;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.IOUtils;

public class FakeSSHKeyCredential extends BaseSSHUser implements SSHUserPrivateKey {
    public static final String ROOT_PATH = "/hudson/plugins/sshslaves/agents";
    public static final String SSH_AGENT_NAME = "ssh-agent-rsa512";
    public static final String SSH_KEY_PATH = "ssh/rsa-512-key";
    public static final String SSH_KEY_PUB_PATH = "ssh/rsa-512-key.pub";

    public static final String ID = "id";
    public static final String USERNAME = "jenkins";
    private final List<String> keys = new ArrayList<>();

    public FakeSSHKeyCredential() throws IOException {
        super(CredentialsScope.SYSTEM, ID, USERNAME, "Fake credentials.");
        String privateKey = IOUtils.toString(
                getClass().getResourceAsStream(ROOT_PATH + "/" + SSH_AGENT_NAME + "/" + SSH_KEY_PATH),
                StandardCharsets.UTF_8);
        keys.add(privateKey);
    }

    @NonNull
    @Override
    public String getPrivateKey() {
        return keys.get(0);
    }

    @Override
    public Secret getPassphrase() {
        return Secret.fromString("");
    }

    @NonNull
    @Override
    public List<String> getPrivateKeys() {
        return keys;
    }
}
