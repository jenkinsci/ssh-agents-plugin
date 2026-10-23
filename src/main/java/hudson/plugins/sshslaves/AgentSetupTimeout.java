package hudson.plugins.sshslaves;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import jenkins.util.Timer;

/**
 * Limits how long the agent setup can take once the SSH connection is open.
 * The connection has no read timeout (JENKINS-48618), so if the agent stops responding during the setup the launch
 * would wait forever, and every later launch of the agent would wait for it.
 * When the time is up the launching thread is interrupted, which makes the waits on the connection fail.
 * Closing the connection instead would not work, as {@code Connection.close()} waits for the same lock as a
 * {@code Connection.openSession()} that waits for the agent.
 */
final class AgentSetupTimeout {

    private enum State {
        RUNNING,
        TIMED_OUT,
        FINISHED
    }

    /**
     * Once the time is up, how often the launching thread is interrupted again, in case it handled the interruption
     * and went on waiting for the agent.
     */
    private static final long INTERRUPT_INTERVAL_SECONDS = 1;

    private final Thread thread;

    // guarded by this, so that the thread is never interrupted once the setup is finished
    private State state = State.RUNNING;

    @CheckForNull
    private ScheduledFuture<?> interrupter;

    private AgentSetupTimeout(Thread thread) {
        this.thread = thread;
    }

    /**
     * Starts the timeout for the current thread.
     *
     * @param timeoutSeconds the time the setup can take, 0 or less to wait forever.
     * @return the timeout, to be finished by the same thread once the setup is over.
     */
    static AgentSetupTimeout start(int timeoutSeconds) {
        AgentSetupTimeout timeout = new AgentSetupTimeout(Thread.currentThread());
        if (timeoutSeconds > 0) {
            timeout.interrupter = Timer.get()
                    .scheduleWithFixedDelay(
                            timeout::expire, timeoutSeconds, INTERRUPT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }
        return timeout;
    }

    /**
     * Called once the time is up, then periodically until the setup is finished.
     */
    synchronized void expire() {
        if (state != State.FINISHED) {
            state = State.TIMED_OUT;
            thread.interrupt();
        }
    }

    /**
     * Stops the timeout once the setup is over, whether it succeeded or not.
     *
     * @return {@code true} if the setup finished in time, {@code false} if the time ran out, in which case the setup
     *     has to be considered failed and the interrupted status of the thread is cleared.
     */
    boolean finish() {
        if (interrupter != null) {
            interrupter.cancel(false);
        }
        synchronized (this) {
            boolean inTime = state == State.RUNNING;
            state = State.FINISHED;
            if (!inTime) {
                // an interruption left over would make the next interruptible operation fail, like writing the log
                Thread.interrupted();
            }
            return inTime;
        }
    }
}
