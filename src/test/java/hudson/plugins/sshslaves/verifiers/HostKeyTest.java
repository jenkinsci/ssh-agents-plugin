package hudson.plugins.sshslaves.verifiers;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * @author Steven Scheffler
 */
class HostKeyTest {

    @Test
    void testFingerprintUsesSHA256() {
        // Example RSA key bytes (this is just test data)
        byte[] keyBytes = "test-key-data".getBytes();
        HostKey hostKey = new HostKey("ssh-rsa", keyBytes);

        String fingerprint = hostKey.getFingerprint();

        // Verify it starts with SHA256: prefix
        assertTrue(fingerprint.startsWith("SHA256:"), "Fingerprint should use SHA256 format");

        // Verify it's Base64 encoded after the prefix
        String base64Part = fingerprint.substring(7); // Remove "SHA256:"
        assertDoesNotThrow(
                () -> Base64.getDecoder().decode(base64Part),
                "Fingerprint should be valid Base64 after SHA256: prefix");
    }

    @Test
    void testFingerprintFormat() {
        byte[] keyBytes = "test-key-data".getBytes();
        HostKey hostKey = new HostKey("ssh-rsa", keyBytes);

        String fingerprint = hostKey.getFingerprint();

        // Should match pattern: SHA256:[Base64]
        assertTrue(fingerprint.matches("SHA256:[A-Za-z0-9+/=]+"), "Fingerprint should match SHA256:Base64 format");
    }
}
