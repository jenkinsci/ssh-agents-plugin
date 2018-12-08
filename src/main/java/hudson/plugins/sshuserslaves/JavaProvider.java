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
package hudson.plugins.sshuserslaves;

import com.trilead.ssh2.Connection;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.slaves.SlaveComputer;
import hudson.model.TaskListener;
import hudson.util.VersionNumber;

import java.util.List;
import java.util.Collections;
import javax.annotation.Nonnull;

/**
 * Guess where Java is.
 */
public abstract class JavaProvider implements ExtensionPoint {
    
    private static final VersionNumber JAVA_LEVEL_8 = new VersionNumber("8");

    /**
     * @deprecated
     *      Override {@link #getJavas(SlaveComputer, TaskListener, Connection)} instead.
     */
    public List<String> getJavas(TaskListener listener, Connection connection) {
        return Collections.emptyList();
    }

    /**
     * Returns the list of possible places where java executable might exist.
     *
     * @return
     *      Can be empty but never null. Absolute path to the possible locations of Java.
     */
    public List<String> getJavas(SlaveComputer computer, TaskListener listener, Connection connection) {
        return getJavas(listener,connection);
    }

    /**
     * All regsitered instances.
     */
    public static ExtensionList<JavaProvider> all() {
        return ExtensionList.lookup(JavaProvider.class);
    }

    /**
     * Gets minimal required Java version.
     * 
     * @return Minimal Java version required on the master and agent side.
     * @since TODO
     * 
     */
    @Nonnull
    public static VersionNumber getMinJavaLevel() {
        return JAVA_LEVEL_8;
    }
}
