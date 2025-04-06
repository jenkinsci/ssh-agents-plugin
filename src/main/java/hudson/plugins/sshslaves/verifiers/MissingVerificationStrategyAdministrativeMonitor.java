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

import java.util.ArrayList;
import java.util.List;
import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import hudson.model.Computer;
import hudson.plugins.sshslaves.Messages;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;

/**
 * An administrative warning that checks all SSH build agents have a {@link SshHostKeyVerificationStrategy}
 * set against them and prompts the admin to update the settings as needed.
 * @author Michael Clarke
 * @since 1.13
 */
@Extension
public class MissingVerificationStrategyAdministrativeMonitor extends AdministrativeMonitor {
    private List<String> agentNames;

    @Override
    public boolean isActivated() {
        agentNames = new ArrayList<>();
        for (Computer computer : Jenkins.get().getComputers()) {
            if (computer instanceof SlaveComputer) {
                ComputerLauncher launcher = ((SlaveComputer) computer).getLauncher();

                if (launcher instanceof SSHLauncher && null == ((SSHLauncher) launcher).getSshHostKeyVerificationStrategy()) {
                    agentNames.add(computer.getDisplayName());
                }
            }
        }
        return agentNames.size() > 0;
    }

    @Override
    public String getDisplayName() {
        return Messages.MissingVerificationStrategyAdministrativeMonitor_DisplayName();
    }

    public String getAgentNames() {
        return agentNames != null ? agentNames.toString() : "";
    }

    @Override
    public boolean isSecurity() {
        return true;
    }
}
