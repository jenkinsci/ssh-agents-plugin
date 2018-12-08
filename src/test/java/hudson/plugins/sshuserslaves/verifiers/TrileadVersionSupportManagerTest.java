package hudson.plugins.sshuserslaves.verifiers;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * @author Michael Clarke
 * @author Zhenlei Huang
 */
public class TrileadVersionSupportManagerTest {

    @Test
    public void testLegacyInstance() {
        BlockingClassloader classloader = newBlockingClassloader();
        classloader.block("com.trilead.ssh2.signature.KeyAlgorithmManager");

        Object trileadSupport = invokeGetTrileadSupport(classloader);
        assertEquals("hudson.plugins.sshslaves.verifiers.TrileadVersionSupportManager$LegacyTrileadVersionSupport", trileadSupport.getClass().getName());
    }

    @Test
    @Issue("JENKINS-44893")
    public void testLegacyInstanceWithLinkageError() {
        BlockingClassloader classloader = newBlockingClassloader();
        classloader.inspectPackage("com.trilead.ssh2.signature");
        classloader.block("com.trilead.ssh2.signature.KeyAlgorithm");

        Object trileadSupport = invokeGetTrileadSupport(classloader);
        assertEquals("hudson.plugins.sshslaves.verifiers.TrileadVersionSupportManager$LegacyTrileadVersionSupport", trileadSupport.getClass().getName());
    }

    @Test
    public void testCurrentInstance() {
        assertEquals(JenkinsTrilead9VersionSupport.class, TrileadVersionSupportManager.getTrileadSupport().getClass());
    }

    @Test
    @Issue("JENKINS-44893")
    public void testCurrentInstanceWithIsolatedClassLoader() {
        BlockingClassloader classloader = newBlockingClassloader();
        Object trileadSupport = invokeGetTrileadSupport(classloader);

        assertEquals("hudson.plugins.sshslaves.verifiers.JenkinsTrilead9VersionSupport", trileadSupport.getClass().getName());
        assertNotEquals(JenkinsTrilead9VersionSupport.class, trileadSupport.getClass());
    }

    private static Object invokeGetTrileadSupport(ClassLoader classloader) {
        try {
            Class<?> clz = Class.forName("hudson.plugins.sshslaves.verifiers.TrileadVersionSupportManager", true, classloader);
            Method method = clz.getDeclaredMethod("getTrileadSupport");
            method.setAccessible(true);
            return method.invoke(null);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static BlockingClassloader newBlockingClassloader() {
        BlockingClassloader classloader = new BlockingClassloader(TrileadVersionSupportManagerTest.class.getClassLoader());
        classloader.inspectPackage("hudson.plugins.sshslaves");
        return classloader;
    }


    private static class BlockingClassloader extends URLClassLoader {

        private final ClassLoader parent;

        private final Set<String> blockingClasses = new HashSet<>();


        public BlockingClassloader(ClassLoader parent) {
            super(new URL[0], parent);
            this.parent = parent;
        }

        @Override
        public Class<?> loadClass(String className) throws ClassNotFoundException {
            if (blockingClasses.contains(className)) {
                throw new ClassNotFoundException(className);
            }

            // child first
            try {
                return super.findClass(className);
            } catch (ClassNotFoundException ignore) {
            }

            return super.loadClass(className);
        }

        public void inspectClass(String clz) {
            URL[] urls = getResourceURLs(parent, clz, true);
            for (URL url : urls) {
                super.addURL(url);
            }
        }

        public void inspectPackage(String pkg) {
            URL[] urls = getResourceURLs(parent, pkg, false);
            for (URL url : urls) {
                super.addURL(url);
            }
        }

        public void block(String clazz) {
            blockingClasses.add(clazz);
        }

        public void unBlock(String clazz) {
            blockingClasses.remove(clazz);
        }

        private static URL[] getResourceURLs(ClassLoader classLoader, String inspect, boolean isClass) {
            String res = inspect.replace('.', '/');
            if (isClass) {
                res = res.concat(".class");
            }
            List<URL> list = new ArrayList<>();
            try {
                Enumeration<URL> enumeration = classLoader.getResources(res);
                while (enumeration.hasMoreElements()) {
                    String url = enumeration.nextElement().toString();
                    if (url.endsWith(res)) {
                        url = url.substring(0, url.length() - res.length());
                    }
                    list.add(new URL(url));
                }
            } catch (IOException ignore) {
            }
            return list.toArray(new URL[0]);
        }
    }
}
