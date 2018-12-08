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
package hudson.plugins.sshuserslaves.verifiers;

import java.io.File;
import java.io.IOException;

import hudson.slaves.ComputerLauncher;
import org.kohsuke.stapler.DataBoundConstructor;

import com.trilead.ssh2.KnownHosts;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.plugins.sshuserslaves.Messages;
import hudson.plugins.sshuserslaves.SSHLauncher;
import hudson.slaves.SlaveComputer;

/**
 * A verifier that reads host keys from the Jenkins users' SSH known_hosts file.
 *
 * @author Michael Clarke
 * @since 1.13
 */
public class KnownHostsFileKeyVerificationStrategy extends SshHostKeyVerificationStrategy {
	
	private static final File KNOWN_HOSTS_FILE = new File(new File(new File(System.getProperty("user.home")), ".ssh"), "known_hosts");

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
            listener.getLogger().println(Messages.KnownHostsFileHostKeyVerifier_NoKnownHostsFile(KNOWN_HOSTS_FILE.getAbsolutePath()));
            return false;
        }
        
        KnownHosts knownHosts = new KnownHosts(KNOWN_HOSTS_FILE);
        int result = knownHosts.verifyHostkey(((SSHLauncher)launcher).getHost(), hostKey.getAlgorithm(), hostKey.getKey());
        
        if (KnownHosts.HOSTKEY_IS_OK == result) {
            listener.getLogger().println(Messages.KnownHostsFileHostKeyVerifier_KeyTrusted(SSHLauncher.getTimestamp()));
            return true;
        } else if (KnownHosts.HOSTKEY_IS_NEW == result) {
            listener.getLogger().println(Messages.KnownHostsFileHostKeyVerifier_NewKeyNotTrusted(SSHLauncher.getTimestamp()));
            return false;
        } else {
            listener.getLogger().println(Messages.KnownHostsFileHostKeyVerifier_ChangedKeyNotTrusted(SSHLauncher.getTimestamp()));
            return false;
        }
        
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

    
    @Extension
    public static class KnownHostsFileKeyVerificationStrategyDescriptor extends SshHostKeyVerificationStrategyDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.KnownHostsFileHostKeyVerifier_DisplayName();
        }
        
    }

}
