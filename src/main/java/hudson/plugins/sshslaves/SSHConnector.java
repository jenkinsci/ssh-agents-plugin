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

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUser;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPassword;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPassword;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import hudson.Extension;
import hudson.Util;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.slaves.ComputerConnector;
import hudson.slaves.ComputerConnectorDescriptor;
import hudson.tools.JDKInstaller;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.putty.PuTTYKey;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;

import static hudson.Util.*;

/**
 * {@link ComputerConnector} for {@link SSHLauncher}.
 *
 * <p>
 * Significant code duplication between this and {@link SSHLauncher} because of the historical reason.
 * Newer plugins like this should define a separate Describable connection parameter class and have
 * connector and launcher share them.
 *
 * @author Kohsuke Kawaguchi
 */
public class SSHConnector extends ComputerConnector {
    /**
     * Field port
     */
    public final int port;

    /**
     * The id of the credentials to use.
     */
    private String credentialsId;

    /**
     * Transient stash of the credentials to use, required during upgrade before the user saves the slave configuration.
     */
    private transient SSHUser credentials;

    /**
     * Field username
     * @deprecated
     */
    @Deprecated
    public transient String username;

    /**
     * Field password
     * @deprecated
     */
    @Deprecated
    public transient Secret password;

    /**
     * File path of the private key.
     * @deprecated
     */
    @Deprecated
    public transient String privatekey;

    /**
     * Field jvmOptions.
     */
    public final String jvmOptions;

    /**
     * Field javaPath.
     */
    public final String javaPath;


    /**
     * Field jdk
     */
    public final JDKInstaller jdkInstaller;

    /**
     * Field prefixStartSlaveCmd. 
     */
    public final String prefixStartSlaveCmd;
    
    /**
     *  Field suffixStartSlaveCmd.
     */
    public final String suffixStartSlaveCmd;
    
    public SSHUser getCredentials() {
        String credentialsId = this.credentialsId == null
                ? (this.credentials == null ? null : this.credentials.getId())
                : this.credentialsId;
        try {
            // only ever want from the system
            // lookup every time so that we always have the latest
            for (SSHUser u: CredentialsProvider.lookupCredentials(SSHUser.class, Hudson.getInstance(), ACL.SYSTEM)) {
                if (StringUtils.equals(u.getId(), credentialsId)) {
                    credentials = u;
                    return u;
                }
            }
        } catch (Throwable t) {
            // ignore
        }
        if (credentials == null) {
            if (credentialsId == null) {
                credentials = SSHLauncher.upgrade(username,password,privatekey,null);
                this.credentialsId = credentials.getId();
            }
        }

        return credentials;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    /**
     * @see SSHLauncher#SSHLauncher(String, int, SSHUser, String, String, String, String)
     */
    @DataBoundConstructor
    public SSHConnector(int port, String credentialsId, String jvmOptions, String javaPath,
                                                                   String prefixStartSlaveCmd, String suffixStartSlaveCmd) {
        this(port, SSHLauncher.lookupSystemCredentials(credentialsId), null, null, null, jvmOptions, javaPath, null, prefixStartSlaveCmd, suffixStartSlaveCmd
        );
    }

    /**
     * @see SSHLauncher#SSHLauncher(String, int, SSHUser, String, String, String, String)
     */
    public SSHConnector(int port, SSHUser credentials, String jvmOptions, String javaPath,
                                                                   String prefixStartSlaveCmd, String suffixStartSlaveCmd) {
        this(port, credentials, null, null, null, jvmOptions, javaPath, null, prefixStartSlaveCmd, suffixStartSlaveCmd
        );
    }

    /**
     * @see SSHLauncher#SSHLauncher(String, int, String, String, String, String, String, String, String)
     */
    public SSHConnector(int port, String username, String password, String privatekey, String jvmOptions, String javaPath,
                                                                   String prefixStartSlaveCmd, String suffixStartSlaveCmd) {
        this(port, null, username, password, privatekey, jvmOptions, javaPath, null, prefixStartSlaveCmd, suffixStartSlaveCmd
        );
    }

    /**
     * @see SSHLauncher#SSHLauncher(String, int, String,String,String, String, String, JDKInstaller, String, String)
     */
    public SSHConnector(int port, String username, String password, String privatekey,
                        String jvmOptions,
                        String javaPath,
                        JDKInstaller jdkInstaller, String prefixStartSlaveCmd, String suffixStartSlaveCmd) {
        this(port, null, username, password, privatekey, jvmOptions, javaPath, jdkInstaller, prefixStartSlaveCmd, suffixStartSlaveCmd);
    }

    /**
     * @see SSHLauncher#SSHLauncher(String, int, SSHUser, String, String, JDKInstaller, String, String)
     */
    public SSHConnector(int port, SSHUser credentials, String username, String password, String privatekey,
                        String jvmOptions,
                        String javaPath,
                        JDKInstaller jdkInstaller, String prefixStartSlaveCmd, String suffixStartSlaveCmd) {
        this.jvmOptions = jvmOptions;
        this.port = port == 0 ? 22 : port;
        this.credentials = credentials;
        this.credentialsId = credentials == null ? null : this.credentials.getId();
        this.username = username;
        this.password = Secret.fromString(fixEmpty(password));
        this.privatekey = privatekey;
        this.javaPath = javaPath;
        this.jdkInstaller = jdkInstaller;
        this.prefixStartSlaveCmd = fixEmpty(prefixStartSlaveCmd);
        this.suffixStartSlaveCmd = fixEmpty(suffixStartSlaveCmd);
    }

    @Override
    public SSHLauncher launch(String host, TaskListener listener) throws IOException, InterruptedException {
        return new SSHLauncher(host,port,getCredentials(),jvmOptions,javaPath,jdkInstaller, prefixStartSlaveCmd, suffixStartSlaveCmd);
    }

    @Extension
    public static class DescriptorImpl extends ComputerConnectorDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.SSHLauncher_DescriptorDisplayName();
        }

        public ListBoxModel doFillCredentialsIdItems() {
            ListBoxModel m = new ListBoxModel();

            for (SSHUser u : CredentialsProvider.lookupCredentials(SSHUser.class,Hudson.getInstance(), ACL.SYSTEM)) {
                m.add(u.getUsername() + (StringUtils.isNotEmpty(u.getDescription())?" (" + u.getDescription() + ")":""), u.getId());
            }

            return m;
        }


    }
}
