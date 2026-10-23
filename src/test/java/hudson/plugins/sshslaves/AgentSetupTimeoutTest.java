package hudson.plugins.sshslaves;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 1, unit = TimeUnit.MINUTES)
class AgentSetupTimeoutTest {

    private static final long WAIT_FOR_AGENT_MILLIS = TimeUnit.SECONDS.toMillis(30);

    @AfterEach
    void clearInterruptedStatus() {
        Thread.interrupted();
    }

    @Test
    void setupFinishedInTime() throws Exception {
        AgentSetupTimeout timeout = AgentSetupTimeout.start(1);
        assertTrue(timeout.finish());
        // the thread must not be interrupted once the setup is over
        Thread.sleep(TimeUnit.SECONDS.toMillis(2));
    }

    @Test
    void threadIsInterruptedWhenTimeIsUp() {
        AgentSetupTimeout timeout = AgentSetupTimeout.start(1);
        assertThrows(InterruptedException.class, () -> Thread.sleep(WAIT_FOR_AGENT_MILLIS));
        assertFalse(timeout.finish());
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void threadIsInterruptedAgainIfItGoesOnWaiting() {
        AgentSetupTimeout timeout = AgentSetupTimeout.start(1);
        assertThrows(InterruptedException.class, () -> Thread.sleep(WAIT_FOR_AGENT_MILLIS));
        assertThrows(InterruptedException.class, () -> Thread.sleep(WAIT_FOR_AGENT_MILLIS));
        assertFalse(timeout.finish());
    }

    @Test
    void threadIsNotInterruptedByTimerCallbackRunningAfterFinish() {
        // a long timeout, the timer callback is run by the test instead
        AgentSetupTimeout timeout = AgentSetupTimeout.start(60);
        timeout.expire();
        assertTrue(Thread.currentThread().isInterrupted());
        assertFalse(timeout.finish());
        assertFalse(Thread.currentThread().isInterrupted());

        // a timer callback that was already running when the setup finished
        timeout.expire();
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void disabledTimeout() throws Exception {
        AgentSetupTimeout timeout = AgentSetupTimeout.start(0);
        Thread.sleep(TimeUnit.SECONDS.toMillis(2));
        assertTrue(timeout.finish());
    }
}
