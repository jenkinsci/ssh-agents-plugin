/*
 * The MIT License
 *
 * Copyright (c) 2016, Michael Clarke
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
package hudson.plugins.sshslaves.verifiers;

import com.trilead.ssh2.KnownHosts;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.TaskListener;
import hudson.plugins.sshslaves.Messages;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A verifier that reads host keys from the Jenkins users' SSH known_hosts file.
 *
 * @author Michael Clarke
 * @since 1.13
 * @deprecated Use {@link hudson.plugins.sshslaves.mina.KnownHostsVerificationStrategy} instead.
 */
@Deprecated
public class KnownHostsFileKeyVerificationStrategy extends SshHostKeyVerificationStrategy {

    public static final String KNOWN_HOSTS_DEFAULT =
            Paths.get(System.getProperty("user.home"), ".ssh", "known_hosts").toString();
    public static final String KNOWN_HOSTS_PROPERTY =
            KnownHostsFileKeyVerificationStrategy.class.getName() + ".known_hosts_file";
    private static final String KNOWN_HOSTS_FILE_PATH =
            StringUtils.defaultIfBlank(System.getProperty(KNOWN_HOSTS_PROPERTY), KNOWN_HOSTS_DEFAULT);
    private static final File KNOWN_HOSTS_FILE = new File(KNOWN_HOSTS_FILE_PATH);

    @DataBoundConstructor
    public KnownHostsFileKeyVerificationStrategy() {
        super();
    }

    @Override
    public boolean verify(SlaveComputer computer, HostKey hostKey, TaskListener listener) throws Exception {
        ComputerLauncher launcher = computer.getLauncher();
        if (!(launcher instanceof SSHLauncher)) {
            return false;
        }

        if (!KNOWN_HOSTS_FILE.exists()) {
            listener.getLogger()
                    .println(Messages.KnownHostsFileHostKeyVerifier_NoKnownHostsFile(
                            KNOWN_HOSTS_FILE.getAbsolutePath()));
            return false;
        }

        SSHLauncher sshLauncher = (SSHLauncher) launcher;
        String host = sshLauncher.getHost();
        String hostPort = host + ":" + sshLauncher.getPort();

        listener.getLogger().println(Messages.KnownHostsFileHostKeyVerifier_SearchingFor(host, KNOWN_HOSTS_FILE));
        int resultHost = verify(host, hostKey.getAlgorithm(), hostKey.getKey());

        listener.getLogger().println(Messages.KnownHostsFileHostKeyVerifier_SearchingFor(hostPort, KNOWN_HOSTS_FILE));
        int resultHostPort = verify(hostPort, hostKey.getAlgorithm(), hostKey.getKey());

        if (KnownHosts.HOSTKEY_IS_OK == resultHost || KnownHosts.HOSTKEY_IS_OK == resultHostPort) {
            listener.getLogger().println(Messages.KnownHostsFileHostKeyVerifier_KeyTrusted(SSHLauncher.getTimestamp()));
            return true;
        } else if (KnownHosts.HOSTKEY_IS_NEW == resultHost && KnownHosts.HOSTKEY_IS_NEW == resultHostPort) {
            listener.getLogger()
                    .println(Messages.KnownHostsFileHostKeyVerifier_NewKeyNotTrusted(SSHLauncher.getTimestamp()));
            return false;
        } else {
            listener.getLogger()
                    .println(Messages.KnownHostsFileHostKeyVerifier_ChangedKeyNotTrusted(SSHLauncher.getTimestamp()));
            return false;
        }
    }

    private int verify(String host, String algorithm, byte[] key) throws IOException {
        KnownHosts knownHosts = new KnownHosts(KNOWN_HOSTS_FILE);
        return knownHosts.verifyHostkey(host, algorithm, key);
    }

    @Override
    public String[] getPreferredKeyAlgorithms(SlaveComputer computer) throws IOException {
        ComputerLauncher launcher = computer.getLauncher();

        if (!(launcher instanceof SSHLauncher) || !KNOWN_HOSTS_FILE.exists()) {
            return super.getPreferredKeyAlgorithms(computer);
        }

        KnownHosts knownHosts = new KnownHosts(KNOWN_HOSTS_FILE);
        return knownHosts.getPreferredServerHostkeyAlgorithmOrder(((SSHLauncher) launcher).getHost());
    }

    @Restricted(NoExternalUse.class)
    public File getKnownHostsFile() {
        return KNOWN_HOSTS_FILE;
    }

    @Extension
    public static class KnownHostsFileKeyVerificationStrategyDescriptor
            extends SshHostKeyVerificationStrategyDescriptor {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.KnownHostsFileHostKeyVerifier_DisplayName();
        }
    }
}
