package hudson.plugins.sshslaves.verifiers;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Michael Clarke
 */
public class TrileadVersionSupportManagerTest {

    @Test
    public void testLegacyInstance() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new BlockingClassloader(Thread.currentThread().getContextClassLoader()));
            String name = TrileadVersionSupportManager.getTrileadSupport().getClass().getName();
            assertEquals("hudson.plugins.sshslaves.verifiers.TrileadVersionSupportManager$LegacyTrileadVersionSupport", name);
        } finally {
            Thread.currentThread().setContextClassLoader(loader);
        }
    }

    @Test
    public void testCurrentInstance() {
        assertEquals(JenkinsTrilead9VersionSupport.class, TrileadVersionSupportManager.getTrileadSupport().getClass());
    }


    private static class BlockingClassloader extends ClassLoader {

        public BlockingClassloader(ClassLoader parent) {
            super(parent);
        }

        public Class<?> loadClass(String className) throws ClassNotFoundException {
            if ("com.trilead.ssh2.signature.KeyAlgorithmManager".equals(className)) {
                throw new ClassNotFoundException(className);
            }
            return super.loadClass(className);
        }

    }
}
