/*
 * The MIT License
 *
 * Copyright (c) 2004-, all the contributors
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
package hudson.plugins.sshslaves;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;
import java.util.logging.Logger;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import hudson.model.TaskListener;
import hudson.slaves.SlaveComputer;
import hudson.util.VersionNumber;
import static hudson.plugins.sshslaves.SSHLauncherConfig.getTimestamp;
import static java.util.logging.Level.FINE;

/**
 * class to check if the version of java installed on the agent is a supported one.
 */
public class JavaVersionChecker {
    private static final Logger LOGGER = Logger.getLogger(JavaVersionChecker.class.getName());

    private final SlaveComputer computer;
    private final TaskListener listener;
    private final String jvmOptions;
    private final SSHProvider connection;

    public JavaVersionChecker(SlaveComputer computer, TaskListener listener, String jvmOptions, SSHProvider connection) {
        this.computer = computer;
        this.listener = listener;
        this.jvmOptions = jvmOptions;
        this.connection = connection;
    }

    /**
     * return javaPath if specified in the configuration.
     * Finds local Java.
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
        throw new IOException("Java not found on " + computer + ". Install a Java 8 version on the Agent.");
    }

    @NonNull
    private String checkJavaVersion(TaskListener listener, String javaCommand) throws IOException, InterruptedException {
        listener.getLogger().println(Messages.SSHLauncher_CheckingDefaultJava(getTimestamp(), javaCommand));
        StringWriter output = new StringWriter();   // record output from Java

        BufferedReader r = connection.exec(javaCommand + " " + jvmOptions + " -version");
        String result = checkJavaVersion(listener.getLogger(), javaCommand, r, output);

        if(result == null) {
            listener.getLogger().println(Messages.SSHLauncher_UnknownJavaVersion(javaCommand));
            listener.getLogger().println(output);
            throw new IOException(Messages.SSHLauncher_UnknownJavaVersion(javaCommand));
        } else {
            return result;
        }
    }

    /**
     * Given the output of "java -version" in <code>r</code>, determine if this
     * version of Java is supported.
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
                                      final BufferedReader r, final StringWriter output) throws IOException {
        String line;
        while (null != (line = r.readLine())) {
            output.write(line);
            output.write("\n");
            line = line.toLowerCase(Locale.ENGLISH);
            if (line.startsWith("java version \"") || line.startsWith("openjdk version \"")) {
                final String versionStr = line.substring(line.indexOf('\"') + 1, line.lastIndexOf('\"'));
                logger.println(Messages.SSHLauncher_JavaVersionResult(getTimestamp(), javaCommand, versionStr));

                // parse as a number and we should be OK as all we care about is up through the first dot.
                final VersionNumber minJavaLevel = JavaProvider.getMinJavaLevel();
                try {
                    final Number version = NumberFormat.getNumberInstance(Locale.US).parse(versionStr);
                    //TODO: burn it with fire
                    if(version.doubleValue() < Double.parseDouble("1."+minJavaLevel)) {
                        throw new IOException(Messages.SSHLauncher_NoJavaFound2(line, minJavaLevel.toString()));
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
