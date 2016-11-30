package hudson.plugins.sshslaves.verifiers;

import java.io.Serializable;
import java.util.Arrays;

import com.trilead.ssh2.KnownHosts;

public final class HostKey implements Serializable {

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