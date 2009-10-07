package hudson.plugins.sshslaves;

import com.trilead.ssh2.ChannelCondition;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.Session;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.Util;
import hudson.Functions;
import static hudson.Functions.defaulted;
import hudson.util.StreamCopyThread;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.remoting.Channel;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Pseudo-{@link Launcher} implementation over SSH.
 *
 * <p>
 * Currently this code only has enough to make JDK auto-installation work.
 *
 * @author Kohsuke Kawaguchi
 */
final class RemoteLauncher extends Launcher {
    private final Connection connection;

    public RemoteLauncher(TaskListener listener, Connection connection) {
        super(listener,null);
        this.connection = connection;
    }

    public Proc launch(ProcStarter ps) throws IOException {
        maskedPrintCommandLine(ps.cmds(), ps.masks(), ps.pwd());

        // TODO: environment variable handling

        String name = ps.cmds().toString();

        final Session session = connection.openSession();
        session.execCommand(makeCommandLine(ps.cmds(),ps.pwd()));
        final Thread t1 = new StreamCopyThread("stdout copier: "+name,session.getStdout(), ps.stdout(),false);
        t1.start();
        final Thread t2 = new StreamCopyThread("stderr copier: "+name,session.getStderr(), defaulted(ps.stderr(),ps.stdout()),false);
        t2.start();
        final Thread t3 = new StreamCopyThread("stdin copier: "+name,ps.stdin(), session.getStdin(),true);
        t3.start();

        return new Proc() {
            public boolean isAlive() throws IOException, InterruptedException {
                return session.getExitStatus()==null;
            }

            public void kill() throws IOException, InterruptedException {
                t1.interrupt();
                t2.interrupt();
                t3.interrupt();
                session.close();
            }

            public int join() throws IOException, InterruptedException {
                try {
                    t1.join();
                    t2.join();
                    t3.join();
                    session.waitForCondition(ChannelCondition.EXIT_STATUS,0);
                    Integer r = session.getExitStatus();
                    if(r!=null) return r;
                    return -1;
                } finally {
                    session.close();
                }
            }
        };
    }


    public Channel launchChannel(String[] cmd, OutputStream out, FilePath _workDir, Map<String, String> envVars) throws IOException, InterruptedException {
        printCommandLine(cmd, _workDir);

        final Session session = connection.openSession();
        session.execCommand(makeCommandLine(Arrays.asList(cmd), _workDir));

        return new Channel("channel over ssh on "+connection.getHostname()+":"+connection.getPort(),
            Computer.threadPoolForRemoting, session.getStdout(), new BufferedOutputStream(session.getStdin()));
    }

    private String makeCommandLine(List<String> cmd, FilePath _workDir) {
        final String workDir = _workDir==null ? null : _workDir.getRemote();
        return "cd '" + workDir + "' && " + Util.join(cmd," "); // TODO: quote handling
    }

    public void kill(Map<String, String> modelEnvVars) throws IOException, InterruptedException {
        // no way to do this
    }
}
