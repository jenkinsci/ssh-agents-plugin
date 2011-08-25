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

import com.trilead.ssh2.SFTPv3DirectoryEntry;
import hudson.tools.JDKInstaller.FileSystem;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link FileSystem} implementation over SFTP.
 *
 * @author Kohsuke Kawaguchi
 */
class SFTPFileSystem implements FileSystem {
    private final SFTPClient sftp;

    public SFTPFileSystem(SFTPClient sftp) {
        this.sftp = sftp;
    }

    public void delete(String file) throws IOException, InterruptedException {
        sftp.rm(file);
    }

    public void chmod(String file, int mode) throws IOException, InterruptedException {
        sftp.chmod(file,mode);
    }

    public InputStream read(String file) throws IOException {
        return new BufferedInputStream(sftp.read(file));
    }

    public List<String> listSubDirectories(String dir) throws IOException, InterruptedException {
        List<String> r = new ArrayList<String>();
        for (SFTPv3DirectoryEntry e : (List<SFTPv3DirectoryEntry>)sftp.ls(dir))
            r.add(e.filename);
        return r;
    }

    public void pullUp(String from, String to) throws IOException, InterruptedException {
        for (SFTPv3DirectoryEntry e : (List<SFTPv3DirectoryEntry>)sftp.ls(from)) {
            if (e.filename.equals(".") || e.filename.equals(".."))  continue;
            sftp.mv(from+'/'+e.filename,to+'/'+e.filename);
        }
        sftp.rmdir(from);
    }
}
