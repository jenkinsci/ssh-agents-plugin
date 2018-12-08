/*
 * The MIT License
 *
 * Copyright (c) 2016, Michael Clarke
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
package hudson.plugins.sshuserslaves.verifiers;

import static org.junit.Assert.assertNotSame;

import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import hudson.plugins.sshuserslaves.SSHConnector;

public class VerificationStrategyConfigurationTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();
    
    @Rule
    public final JenkinsRule jenkins = new JenkinsRule();
    
    @Test
    public void testConfigureRoundTripManualTrustedStrategy() throws Exception {
        testConfigureRoundTrip(new ManuallyTrustedKeyVerificationStrategy(true));
    }
    
    @Test
    public void testConfigureRoundTripNonVerifyingStrategy() throws Exception {
        testConfigureRoundTrip(new NonVerifyingKeyVerificationStrategy());
    }
    
    @Test
    public void testConfigureRoundTripManualProvidedVerifyingStrategy() throws Exception {
        String key = "AAAAB3NzaC1yc2EAAAADAQABAAABAQC1oF3jpBkexmWgKh7kwMGFjb9L7+/mvY7TNMiobWC4JK8T" +
                "7fv/gRNMSfY6Fg9INZosfxD+9oktnVl1/9Nc5Qqp3/ia7qtyccXzab6WuNbuos+Ggb14vqLe0SD+" +
                "Edc1TpBRMg8w70L41uTlgrhHqwzt96BbPe9hG1cfgZ5Lx9JTMZUyXgGaJmShE9Fsa+CJV5bW/Nqc" +
                "8G/Z8fLKBlUwiX7hQHkG4xVNQve60kDvDVJpozd+XAiZrQVgwCLTg3ik2aDdR9U+VCC7q1s3SgFF" +
                "f8jh5Z5QAJ2MA+A6oq2rJJoCIfXJnBdXEgHggJf3d1tl1vBI1pOVxDa9BWBjr4KvwgwL";
        testConfigureRoundTrip(new ManuallyProvidedKeyVerificationStrategy("ssh-rsa " + key));
    }
    
    @Test
    public void testConfigureRoundTripKnownHostsVerifyingStrategy() throws Exception {
        testConfigureRoundTrip(new KnownHostsFileKeyVerificationStrategy());
    }
    
    private void testConfigureRoundTrip(SshHostKeyVerificationStrategy strategy) throws Exception {
        StandardUsernameCredentials credentials = new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM, "dummyCredentialId", null, "dummyUser", "dummyPassword");

        List<Credentials> credentialsList = new ArrayList<>();
        credentialsList.add(credentials);
        SystemCredentialsProvider.getInstance().getDomainCredentialsMap().put(Domain.global(), credentialsList);
        
        SSHConnector connector = new SSHConnector(12, credentials, null, null, null, "", "xyz", null, "", "", 30, 1, 1, strategy);
        
        SSHConnector output = jenkins.configRoundtrip(connector);

        assertNotSame(connector, output);
        jenkins.assertEqualDataBoundBeans(connector, output);
        
        
    }
    
}
