package hudson.plugins.sshslaves;

import com.trilead.ssh2.Connection;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.slaves.SlaveComputer;
import hudson.model.Hudson;
import hudson.model.TaskListener;

import java.util.List;
import java.util.Collections;

/**
 * Guess where Java is.
 */
public abstract class JavaProvider implements ExtensionPoint {
    /**
     * @deprecated
     *      Override {@link #getJavas(SlaveComputer, TaskListener, Connection)} instead.
     */
    public List<String> getJavas(TaskListener listener, Connection connection) {
        return Collections.emptyList();
    }

    /**
     * Returns the list of possible places where java executable might exist.
     *
     * @return
     *      Can be empty but never null. Absolute path to the possible locations of Java.
     */
    public List<String> getJavas(SlaveComputer computer, TaskListener listener, Connection connection) {
        return getJavas(listener,connection);
    }

    /**
     * All regsitered instances.
     */
    public static ExtensionList<JavaProvider> all() {
        return Hudson.getInstance().getExtensionList(JavaProvider.class);
    }

}
