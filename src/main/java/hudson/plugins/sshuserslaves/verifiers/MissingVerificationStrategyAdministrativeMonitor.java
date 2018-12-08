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
import hudson.model.AdministrativeMonitor;
import hudson.model.Computer;
import hudson.plugins.sshuserslaves.Messages;
import hudson.plugins.sshuserslaves.SSHLauncher;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import hudson.util.VersionNumber;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

/**
 * An administrative warning that checks all SSH slaves have a {@link SshHostKeyVerificationStrategy}
 * set against them and prompts the admin to update the settings as needed.
 * @author Michael Clarke
 * @since 1.13
 */
@Extension
public class MissingVerificationStrategyAdministrativeMonitor extends AdministrativeMonitor {

    @Override
    public boolean isActivated() {
        for (Computer computer : Jenkins.getInstance().getComputers()) {
            if (computer instanceof SlaveComputer) {
                ComputerLauncher launcher = ((SlaveComputer) computer).getLauncher();

                if (launcher instanceof SSHLauncher && null == ((SSHLauncher) launcher).getSshHostKeyVerificationStrategy()) {
                    return true;
                }
            }
        }

        return false;
    }

    //TODO: This method can be removed when the baseline is updated to 2.103.
    /**
     * @return true if this version of the plugin is running on a Jenkins version where JENKINS-43786 is included.
     */
    @Restricted(DoNotUse.class)
    public boolean isTheNewDesignAvailable() {
        final VersionNumber version = Jenkins.getVersion();
        if (version != null && version.isNewerThan(new VersionNumber("2.103"))) {
            return true;
        }
        return false;
    }

    @Override
    public String getDisplayName() {
        return Messages.MissingVerificationStrategyAdministrativeMonitor_DisplayName();
    }
}
