package hudson.plugins.sshslaves;

import hudson.Plugin;
import hudson.slaves.ComputerLauncher;

import java.util.logging.Logger;

/**
 * Entry point of ssh-slaves plugin.
 *
 * @author Stephen Connolly
 * @plugin
 */
public class PluginImpl extends Plugin {

    public void start() throws Exception {
        ComputerLauncher.LIST.add(SSHLauncher.DESCRIPTOR);
    }

    private static final java.util.logging.Logger LOGGER = Logger.getLogger(PluginImpl.class.getName());
}
