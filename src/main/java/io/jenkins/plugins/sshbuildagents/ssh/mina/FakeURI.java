package io.jenkins.plugins.sshbuildagents.ssh.mina;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.sshd.common.util.io.resource.URIResource;

/**
 * A fake URI resource that simulates a URI with a key as its content. This is used for testing
 * purposes to provide a simple way to create a resource that contains a key string.
 */
public class FakeURI extends URIResource {
    private final String key;

    public FakeURI(String key) throws URISyntaxException {
        super(new URI("fake://key"));
        this.key = key;
    }

    @Override
    public InputStream openInputStream() throws IOException {
        return new ByteArrayInputStream(this.key.getBytes("UTF-8"));
    }
}
