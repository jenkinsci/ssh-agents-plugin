package hudson.plugins.sshslaves;

import com.trilead.ssh2.Connection;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Hudson;
import hudson.model.TaskListener;

import java.util.List;

/**
 * Guess where Java is.
 */
public abstract class JavaProvider implements ExtensionPoint {
    public abstract List<String> getJavas(TaskListener listener, Connection connection);

    /**
     * All regsitered instances.
     */
    public static ExtensionList<JavaProvider> all() {
        return Hudson.getInstance().getExtensionList(JavaProvider.class);
    }

}
