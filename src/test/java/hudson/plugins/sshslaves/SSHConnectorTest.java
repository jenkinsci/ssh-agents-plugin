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

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.util.FormValidation;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for <a href="https://github.com/jenkinsci/ssh-agents-plugin/issues/899">issue #899</a>:
 * launch timeout values below the recommended minimum were accepted silently, which can make agent
 * connections flaky.
 */
class SSHConnectorTest {

    private final SSHConnector.DescriptorImpl descriptor = new SSHConnector.DescriptorImpl();

    @Test
    void warnsWhenTimeoutBelowRecommendedMinimum() {
        FormValidation result = descriptor.doCheckLaunchTimeoutSeconds("5");
        assertEquals(FormValidation.Kind.WARNING, result.kind);
    }

    @Test
    void okWhenTimeoutAtRecommendedMinimum() {
        FormValidation result =
                descriptor.doCheckLaunchTimeoutSeconds(String.valueOf(SSHLauncher.DEFAULT_LAUNCH_TIMEOUT_SECONDS));
        assertEquals(FormValidation.Kind.OK, result.kind);
    }

    @Test
    void okWhenTimeoutAboveRecommendedMinimum() {
        FormValidation result = descriptor.doCheckLaunchTimeoutSeconds("120");
        assertEquals(FormValidation.Kind.OK, result.kind);
    }

    @Test
    void okWhenTimeoutBlank() {
        FormValidation result = descriptor.doCheckLaunchTimeoutSeconds("");
        assertEquals(FormValidation.Kind.OK, result.kind);
    }

    @Test
    void errorsWhenTimeoutNegative() {
        FormValidation result = descriptor.doCheckLaunchTimeoutSeconds("-1");
        assertEquals(FormValidation.Kind.ERROR, result.kind);
    }

    @Test
    void errorsWhenTimeoutNotANumber() {
        FormValidation result = descriptor.doCheckLaunchTimeoutSeconds("notanumber");
        assertEquals(FormValidation.Kind.ERROR, result.kind);
    }

    @Test
    void okWhenTimeoutZero() {
        // 0 falls back to the default elsewhere (SSHLauncher/SSHConnector setters); it is not itself
        // "below the minimum but positive", so it should not warn here.
        FormValidation result = descriptor.doCheckLaunchTimeoutSeconds("0");
        assertEquals(FormValidation.Kind.OK, result.kind);
    }
}
