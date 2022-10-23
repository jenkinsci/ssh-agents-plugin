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

import java.io.IOException;

import javax.servlet.ServletException;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import hudson.model.Computer;
import hudson.model.TaskAction;
import hudson.plugins.sshslaves.Messages;
import hudson.security.ACL;
import hudson.security.Permission;

/**
 * An action that prompts a user with Computer.CONFIGURE privileges to trust a public key
 * issued by a remote SSH host. If a key is already known for this host then the user will
 * be prompted to replace the existing key, otherwise they will be prompted to add a new
 * key.
 * @author Michael Clarke
 * @since 1.13
 */
public class TrustHostKeyAction extends TaskAction  {

    private static int keyNumber = 0;
    private final HostKey hostKey;
    private final Computer computer;
    private final String actionPath;

    private boolean complete;

    @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD",
            justification = "Need a static counter of all instances that have been created")
    TrustHostKeyAction(Computer computer, HostKey hostKey) {
        super();
        this.hostKey = hostKey;
        this.computer = computer;
        this.actionPath = "saveHostKey-" + keyNumber++;
    }

    public HostKey getHostKey() {
        return hostKey;
    }

    public HostKey getExistingHostKey() throws IOException {
        return HostKeyHelper.getInstance().getHostKey(getComputer());
    }

    public Computer getComputer() {
        return computer;
    }

    @RequirePOST
    public void doSubmit(StaplerRequest request, StaplerResponse response) throws IOException, ServletException {
        getACL().checkPermission(getPermission());

        if (null != request.getParameter("Yes")) {
            HostKeyHelper.getInstance().saveHostKey(getComputer(), getHostKey());
        } else if (null == request.getParameter("No")) {
            throw new IOException("Invalid action");
        }

        complete = true;
        response.sendRedirect("../");
    }

    @RequirePOST
    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        req.getView(this, "trustHostKey").forward(req, rsp);
    }

    public boolean isComplete() {
        return complete;
    }

    @Override
    public String getIconFileName() {
        if (complete || !getACL().hasPermission(getPermission())) {
            return null;
        }
        return "save.gif";
    }

    @Override
    public String getDisplayName() {
        if (complete || !getACL().hasPermission(getPermission())) {
            return null;
        }
        return Messages.TrustHostKeyAction_DisplayName();
    }

    @Override
    protected Permission getPermission() {
        return Computer.CONFIGURE;
    }

    @Override
    protected ACL getACL() {
        return computer.getACL();
    }

    @Override
    public String getUrlName() {
        if (complete || !getACL().hasPermission(getPermission())) {
            return null;
        }
        return actionPath;
    }
}