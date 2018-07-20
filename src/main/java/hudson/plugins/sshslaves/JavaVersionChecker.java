package hudson.plugins.sshslaves;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import com.trilead.ssh2.Connection;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import hudson.model.TaskListener;
import hudson.slaves.SlaveComputer;
import hudson.util.VersionNumber;
import static java.util.logging.Level.FINE;

/**
 * class to check if the version of java installed on the agent is a supported one.
 */
public class JavaVersionChecker {
    private static final Logger LOGGER = Logger.getLogger(JavaVersionChecker.class.getName());

    private final SlaveComputer computer;
    private final TaskListener listener;
    private final String jvmOptions;
    private final Connection connection;

    public JavaVersionChecker(SlaveComputer computer, TaskListener listener, String jvmOptions, Connection connection) {
        this.computer = computer;
        this.listener = listener;
        this.jvmOptions = jvmOptions;
        this.connection = connection;
    }

    /**
     * return javaPath if specified in the configuration.
     * Finds local Java, and if none exist, install one.
     */
    protected String resolveJava() throws InterruptedException, IOException {
        for (JavaProvider provider : JavaProvider.all()) {
            for (String javaCommand : provider.getJavas(computer, listener, connection)) {
                LOGGER.fine("Trying Java at " + javaCommand);
                try {
                    return checkJavaVersion(listener, javaCommand);
                } catch (IOException e) {
                    LOGGER.log(FINE, "Failed to check the Java version",e);
                    // try the next one
                }
            }
        }
        throw new IOException("Java not found " + computer + ", install a Java 8+ version on the Agent, if you need "
                              + "others JDK use the Global Tool Configuration to install them.");
    }

    @NonNull
    private String checkJavaVersion(TaskListener listener, String javaCommand) throws IOException, InterruptedException {
        listener.getLogger().println(Messages.SSHLauncher_CheckingDefaultJava(SSHLauncher.getTimestamp(),javaCommand));
        StringWriter output = new StringWriter();   // record output from Java

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        connection.exec(javaCommand + " "+ jvmOptions + " -version",out);
        //TODO: Seems we need to retrieve the encoding from the connection destination
        BufferedReader r = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(out.toByteArray()), Charset.defaultCharset()));
        final String result = checkJavaVersion(listener.getLogger(), javaCommand, r, output);

        if(null == result) {
            listener.getLogger().println(Messages.SSHLauncher_UnknownJavaVersion(javaCommand));
            listener.getLogger().println(output);
            throw new IOException(Messages.SSHLauncher_UnknownJavaVersion(javaCommand));
        } else {
            return result;
        }
    }

    // XXX switch to standard method in 1.479+
    /**
     * Given the output of "java -version" in <code>r</code>, determine if this
     * version of Java is supported. This method has default visiblity for testing.
     *
     * @param logger
     *            where to log the output
     * @param javaCommand
     *            the command executed, used for logging
     * @param r
     *            the output of "java -version"
     * @param output
     *            copy the data from <code>r</code> into this output buffer
     */
    @CheckForNull
    @Restricted(NoExternalUse.class)
    public String checkJavaVersion(final PrintStream logger, String javaCommand,
                                      final BufferedReader r, final StringWriter output)
            throws IOException {
        String line;
        while (null != (line = r.readLine())) {
            output.write(line);
            output.write("\n");
            line = line.toLowerCase(Locale.ENGLISH);
            if (line.startsWith("java version \"")
                || line.startsWith("openjdk version \"")) {
                final String versionStr = line.substring(
                        line.indexOf('\"') + 1, line.lastIndexOf('\"'));
                logger.println(Messages.SSHLauncher_JavaVersionResult(
                        SSHLauncher.getTimestamp(), javaCommand, versionStr));

                // parse as a number and we should be OK as all we care about is up through the first dot.
                final VersionNumber minJavaLevel = JavaProvider.getMinJavaLevel();
                try {
                    final Number version =
                            NumberFormat.getNumberInstance(Locale.US).parse(versionStr);
                    //TODO: burn it with fire
                    if(version.doubleValue() < Double.parseDouble("1."+minJavaLevel)) {
                        throw new IOException(Messages
                                                      .SSHLauncher_NoJavaFound2(line, minJavaLevel.toString()));
                    }
                } catch(final ParseException e) {
                    throw new IOException(Messages.SSHLauncher_NoJavaFound2(line, minJavaLevel));
                }
                return javaCommand;
            }
        }
        return null;
    }
}
