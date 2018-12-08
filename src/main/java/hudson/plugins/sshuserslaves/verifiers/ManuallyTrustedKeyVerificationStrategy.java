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

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.plugins.sshuserslaves.Messages;
import hudson.plugins.sshuserslaves.SSHLauncher;
import hudson.slaves.SlaveComputer;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A host key verification strategy that works in a similar way to host key verification on
 * Unix/Linux this host (depending on how this strategy has been configured), and manual
 * verification if the key provided by the remote host differs from the one currently saved
 * in as the known key for this host. This manual verification is achieved through adding a
 * {@link TrustHostKeyAction } to the Computer the connection is being initiated for that can
 * be actioned by a user with the appropriate permission to add a new key or replace an existing
 * key in the known hosts database.
 * @author Michael Clarke
 * @since 1.13
 */
public class ManuallyTrustedKeyVerificationStrategy extends SshHostKeyVerificationStrategy {

    private static final Logger LOGGER = Logger.getLogger(ManuallyTrustedKeyVerificationStrategy.class.getName());
    
    private final boolean requireInitialManualTrust;
    
    @DataBoundConstructor
    public ManuallyTrustedKeyVerificationStrategy(boolean requireInitialManualTrust) {
        super();
        this.requireInitialManualTrust = requireInitialManualTrust;
    }
    
    public boolean isRequireInitialManualTrust() {
        return requireInitialManualTrust;
    }
    
    @Override
    public boolean verify(final SlaveComputer computer, HostKey hostKey, TaskListener listener) throws IOException {
        HostKeyHelper hostManager = HostKeyHelper.getInstance();
        
        HostKey existingHostKey = hostManager.getHostKey(computer);
        if (null == existingHostKey) {
            if (isRequireInitialManualTrust()) {
                listener.getLogger().println(Messages.ManualTrustingHostKeyVerifier_KeyNotTrusted(SSHLauncher.getTimestamp()));
                if (!hasExistingTrustAction(computer, hostKey)) {
                    addAction(computer, new TrustHostKeyAction(computer, hostKey));
                }
                return false;
            }
            else {
                listener.getLogger().println(Messages.ManualTrustingHostKeyVerifier_KeyAutoTrusted(SSHLauncher.getTimestamp(), hostKey.getFingerprint()));
                HostKeyHelper.getInstance().saveHostKey(computer, hostKey);
                return true;
            }
        }
        else if (!existingHostKey.equals(hostKey)) {
            listener.getLogger().println(Messages.ManualTrustingHostKeyVerifier_KeyNotTrusted(SSHLauncher.getTimestamp()));
            if (!hasExistingTrustAction(computer, hostKey)) {
                addAction(computer, new TrustHostKeyAction(computer, hostKey));
            }
            return false;
        }
        else {
            listener.getLogger().println(Messages.ManualTrustingHostKeyVerifier_KeyTrusted(SSHLauncher.getTimestamp()));
            return true;
        }
    }

    @Override
    public String[] getPreferredKeyAlgorithms(SlaveComputer computer) throws IOException {
        String[] algorithms = super.getPreferredKeyAlgorithms(computer);

        HostKey hostKey = HostKeyHelper.getInstance().getHostKey(computer);

        if (null != hostKey) {
            List<String> sortedAlgorithms = new ArrayList<>(Arrays.asList(algorithms));

            sortedAlgorithms.remove(hostKey.getAlgorithm());
            sortedAlgorithms.add(0, hostKey.getAlgorithm());

            algorithms = sortedAlgorithms.toArray(new String[0]);
        }

        return algorithms;
    }

    /** TODO replace with {@link Computer#addAction} after core baseline picks up JENKINS-42969 fix */
    private static void addAction(@Nonnull Computer c, @Nonnull Action a) {
        try {
            c.addAction(a);
        } catch (UnsupportedOperationException x) {
            try {
                Field actionsF = Actionable.class.getDeclaredField("actions");
                actionsF.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<Action> actions = (List) actionsF.get(c);
                actions.add(a);
            } catch (Exception x2) {
                LOGGER.log(Level.WARNING, null, x2);
            }
        }
    }
    
    private boolean hasExistingTrustAction(SlaveComputer computer, HostKey hostKey) {
        for (TrustHostKeyAction action : computer.getActions(TrustHostKeyAction.class)) {
            if (!action.isComplete() && action.getHostKey().equals(hostKey)) {
                return true;
            }
        }
        
        return false;
    }
    
    @Extension
    public static class ManuallyTrustedKeyVerificationStrategyDescriptor extends SshHostKeyVerificationStrategyDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.ManualTrustingHostKeyVerifier_DescriptorDisplayName();
        }
        
    }
    
}
