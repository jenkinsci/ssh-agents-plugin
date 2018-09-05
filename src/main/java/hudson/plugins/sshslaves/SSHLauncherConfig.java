package hudson.plugins.sshslaves;

import java.util.Date;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import hudson.plugins.sshslaves.verifiers.SshHostKeyVerificationStrategy;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;

public interface SSHLauncherConfig {

    /**
     * Gets the formatted current time stamp.
     *
     * @return the formatted current time stamp.
     */
    @Restricted(NoExternalUse.class)
    static String getTimestamp(){
        return String.format("[%1$tD %1$tT]", new Date());
    }

    String getCredentialsId();

    @NonNull
    SshHostKeyVerificationStrategy getSshHostKeyVerificationStrategyDefaulted();

    @CheckForNull
    SshHostKeyVerificationStrategy getSshHostKeyVerificationStrategy();

    StandardUsernameCredentials getCredentials();

    String getJvmOptions();

    String getHost();

    int getPort();

    @NonNull
    String getPrefixStartSlaveCmd();

    @NonNull
    String getSuffixStartSlaveCmd();

    @NonNull
    Integer getLaunchTimeoutSeconds();

    long getLaunchTimeoutMillis();

    @NonNull
    Integer getMaxNumRetries();

    @NonNull
    Integer getRetryWaitTime();

    boolean getTcpNoDelay();

    boolean getTrackCredentials();

    String getWorkDir();

    String getWorkDirParam(@NonNull String workingDirectory);

    String getJavaPath();
}
