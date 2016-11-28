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
import java.util.Map;
import java.util.WeakHashMap;

import com.trilead.ssh2.KnownHosts;

import hudson.XmlFile;
import hudson.model.Computer;
import hudson.model.Node;
import jenkins.model.Jenkins;

public final class HostKeyManager {

    private static final HostKeyManager INSTANCE = new HostKeyManager();
    
    private final Map<Computer, HostKey> cache = new WeakHashMap<Computer, HostKey>();

    private HostKeyManager() {
        super();
    }

    public static HostKeyManager getInstance() {
        return INSTANCE;
    }


    public HostKey getHostKey(Computer host) throws IOException {
        HostKey key = cache.get(host);
        if (null == key) {
            File hostKeyFile = new File(new File(new File(Jenkins.getInstance().getRootDir(), "nodes"), host.getName()), "ssh-host-key.xml");
            if (hostKeyFile.exists()) {
                XmlFile xmlHostKeyFile = new XmlFile(hostKeyFile);
                key = (HostKey) xmlHostKeyFile.read();
            } else {
                key = null;
            }
            cache.put(host, key);
        }
        return key;
    }

    public void saveHostKey(Computer host, HostKey hostKey) throws IOException {
        cache.put(host, hostKey);
        XmlFile xmlHostKeyFile = new XmlFile(getSshHostKeyFile(host.getNode()));
        xmlHostKeyFile.write(hostKey);
    }
    
    private File getSshHostKeyFile(Node node) {
        return new File(getNodeDirectory(node), "ssh-host-key.xml");
    }
    
    private File getNodeDirectory(Node node) {
        return new File(getNodesDirectory(), node.getNodeName());
    }
    
    private File getNodesDirectory() {
        return new File(Jenkins.getInstance().getRootDir(), "nodes");
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
