package hudson.plugins.sshslaves;

import hudson.util.StreamTaskListener;
import hudson.ExtensionPoint;
import hudson.ExtensionList;
import hudson.model.Hudson;

import java.util.List;

import com.trilead.ssh2.Connection;

/**
 * Guess where Java is.
 */
public abstract class JavaProvider implements ExtensionPoint {
    public abstract List<String> getJavas(StreamTaskListener listener, Connection connection);

    /**
     * All regsitered instances.
     */
    public static ExtensionList<JavaProvider> all() {
        return Hudson.getInstance().getExtensionList(JavaProvider.class);
    }

}
