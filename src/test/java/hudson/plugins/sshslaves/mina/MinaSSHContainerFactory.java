package hudson.plugins.sshslaves.mina;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * Shared factory for creating SSH testcontainers for Mina SSH tests.
 */
class MinaSSHContainerFactory {

    static GenericContainer<?> createContainer(String target) {
        return new GenericContainer<>(new ImageFromDockerfile(
                                "localhost/testcontainers/mina-sshd-" + target,
                                Boolean.parseBoolean(System.getenv("CI")))
                        .withFileFromClasspath(".", "/hudson/plugins/sshslaves/mina/docker")
                        .withTarget(target))
                .withExposedPorts(22);
    }

    private MinaSSHContainerFactory() {}
}
