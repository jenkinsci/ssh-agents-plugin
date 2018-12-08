package hudson.plugins.sshuserslaves.verifiers;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;

/**
 * @author Michael Clarke
 */
public class ManuallyProvidedKeyVerificationStrategyTest {

    @Test
    public void testRsa() throws IOException {
        ManuallyProvidedKeyVerificationStrategy testCase = new ManuallyProvidedKeyVerificationStrategy("ssh-rsa AAAAB3NzaC1yc2EAAAABJQAAAQEAtqwn/v4+sYBD0e5UT59zGjQ+iBOJvKbqVX22vt4hFIVrbwmB+HKJGwOINe1gnc/syPGj/5c6yoOnjTdpI/xerip6RjVPRTQVh2nNjsbXIS5epi/39nnPFZ/0hE3ozOtQ1j9OS5bXVBD770ha1UFnCql4DfcWj+y1QVYvm53p2fID+an0HNunnZjq+r2UJgt138lkZN2K7S42U/apqOHStFGVPxF+gmK1fI021QI+QjxfKOoyGNCpbAaMM6jzikqCJOE8M7jpSZgHMO2x+wvjMK8p2uXAaZlYJeUlEqUVGa9jjkdEiTPabFJyrKORrTWX7Ahs6C4vCAgWmNZzOmOvnw== rsa-key-20170516");
        assertArrayEquals(new String[]{"ssh-rsa", "ssh-ed25519", "ecdsa-sha2-nistp521", "ecdsa-sha2-nistp384", "ecdsa-sha2-nistp256", "ssh-dss"}, testCase.getPreferredKeyAlgorithms(null));
    }

    @Test
    public void testEd25519() throws IOException {
        ManuallyProvidedKeyVerificationStrategy testCase = new ManuallyProvidedKeyVerificationStrategy("ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIMQPcXch45Uak9iiHt1puffR6LHZxZsHU0iyeyUnf5qW ed25519-key-20170516");
        assertArrayEquals(new String[]{"ssh-ed25519", "ecdsa-sha2-nistp521", "ecdsa-sha2-nistp384", "ecdsa-sha2-nistp256", "ssh-rsa", "ssh-dss"}, testCase.getPreferredKeyAlgorithms(null));
    }


    @Test
    public void testEcdsa() throws IOException {
        ManuallyProvidedKeyVerificationStrategy testCase = new ManuallyProvidedKeyVerificationStrategy("ecdsa-sha2-nistp256 AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBMQMVHTpplIuqEcOR8j7wzydDUzXF0Fl82WluEJphpo2JKbJ4DNaL3Zu6bfeDQGuH3hWtG1H0r4ntoDtN940GGA= ecdsa-key-20170516");
        assertArrayEquals(new String[]{"ecdsa-sha2-nistp256", "ssh-ed25519", "ecdsa-sha2-nistp521", "ecdsa-sha2-nistp384", "ssh-rsa", "ssh-dss"}, testCase.getPreferredKeyAlgorithms(null));
    }

    @Test
    public void testDsa() throws IOException {
        ManuallyProvidedKeyVerificationStrategy testCase = new ManuallyProvidedKeyVerificationStrategy("ssh-dss AAAAB3NzaC1kc3MAAAAhAOD3H2nbagBMaZ7XDnGUBO3vuqi3McIC9A+smJH9lsnzAAAAFQD3lLxlCXN8K4CeNCJdHeXEpeE7vwAAACBtZ3osIr0OtX6uKFumP6ybXGrfiy7otYqmSPwS+A2MywAAACEA34SUyAprA9HHPmRqZnJ6Acgq6KKRrh4SKTPUdJa8aBc= dsa-key-20170516");
        assertArrayEquals(new String[]{"ssh-dss", "ssh-ed25519", "ecdsa-sha2-nistp521", "ecdsa-sha2-nistp384", "ecdsa-sha2-nistp256", "ssh-rsa"}, testCase.getPreferredKeyAlgorithms(null));
    }

}
