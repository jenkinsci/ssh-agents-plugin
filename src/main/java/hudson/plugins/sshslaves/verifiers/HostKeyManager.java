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
package hudson.plugins.sshslaves.verifiers;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.trilead.ssh2.KnownHosts;

import hudson.BulkChange;
import hudson.XmlFile;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import jenkins.model.Jenkins;

public final class HostKeyManager implements Saveable {

    private static final HostKeyManager INSTANCE = new HostKeyManager();
    private static final Logger LOGGER = Logger.getLogger(HostKeyManager.class.getName());

    private final Map<HostIdentifier, HostKey> trustedKeys = new HashMap<HostIdentifier, HostKey>();


    private HostKeyManager() {
        super();
        try {
            load();
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Could not load known hosts entries", ex);
        }
    }



    public static HostKeyManager getInstance() {
        return INSTANCE;
    }


    public HostKey getHostKey(HostIdentifier host) {
        return trustedKeys.get(host);
    }

    public void saveHostKey(HostIdentifier host, HostKey hostKey) {
        trustedKeys.put(host, hostKey);
        try {
            save();
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Could not save changes to known hosts entries", ex);
        }
    }

     void load() throws IOException {
        XmlFile file = getConfigFile();
        if(!file.exists()) {
            return;
        }

        file.unmarshal(this);
    }





    public synchronized void save() throws IOException {
        if(BulkChange.contains(this)) {
            return;
        }
        getConfigFile().write(this);
        SaveableListener.fireOnChange(this, getConfigFile());
    }

    private XmlFile getConfigFile() {
        return new XmlFile(new File(Jenkins.getInstance().getRootDir(), "ssh-known-hosts.xml"));
    }



    public static class HostIdentifier implements Serializable {

        private static final long serialVersionUID = -8438646557775748159L;

        private final String hostname;
        private final int port;

        public HostIdentifier(String hostname, int port) {
            super();
            this.hostname = hostname;
            this.port = port;
        }

        public String getHostname() {
            return hostname;
        }

        public int getPort() {
            return port;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((hostname == null) ? 0 : hostname.hashCode());
            result = prime * result + port;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            HostIdentifier other = (HostIdentifier) obj;
            if (hostname == null) {
                if (other.hostname != null)
                    return false;
            } else if (!hostname.equals(other.hostname))
                return false;
            if (port != other.port)
                return false;
            return true;
        }
    }



    public static final class HostKey implements Serializable {

        private static final long serialVersionUID = -5131839381842616910L;

        private final String algorithm;
        private final byte[] key;

        public HostKey(String algorithm, byte[] key) {
            super();
            this.algorithm = algorithm;
            this.key = key;
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public byte[] getKey() {
            return key;
        }

        public String getFingerprint() {
            return KnownHosts.createHexFingerprint(getAlgorithm(), getKey());
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((algorithm == null) ? 0 : algorithm.hashCode());
            result = prime * result + Arrays.hashCode(key);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            HostKey other = (HostKey) obj;
            if (algorithm == null) {
                if (other.algorithm != null)
                    return false;
            } else if (!algorithm.equals(other.algorithm))
                return false;
            if (!Arrays.equals(key, other.key))
                return false;
            return true;
        }
    }
}
