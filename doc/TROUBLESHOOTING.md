
## Troubleshooting

### Common Pitfalls
#### Login profile files
When the SSH slaves plugin connects to a agent, it does not run an interactive shell. 
Instead it does the equivalent of running `ssh agenthost command...` a few times, 
eventually running `ssh agenthost java -jar ...`. Exactly what happens on the agent as a result of this depends on the SSHD implementation, OpenSSH runs this with `bash -c command ...` (or whatever your login shell is.)

This means some of your login profiles that set up your environment are not read by your shell. See [this post](http://stackoverflow.com/questions/216202/why-does-an-ssh-remote-command-get-fewer-environment-variables-then-when-run-man) for more details.

If your login shell does not understand the command syntax used (e.g. the fish shell), use the advanced options **Prefix Start agent Command** and **Suffix Start agent Command** to wrap the agent command in e.g. `sh -c " and "`.

Example: For loading `~/.bash_profile` on macOS agents set **Prefix Start agent Command** to `source ~/.bash_profile && ` (intended whitespace at the end).
Make sure to reconnect the agent after changing the agents commands.

#### Overall recommendations:
* Use a JDK version in the same major version as the Jenkins instance and agents and preferably a close minor version.
* Tune the TCP stack on of Jenkins instance and agents
    * [Linux](http://tldp.org/HOWTO/TCP-Keepalive-HOWTO/usingkeepalive.html)
    * [Windows](https://blogs.technet.microsoft.com/nettracer/2010/06/03/things-that-you-may-want-to-know-about-tcp-keepalives/)
    * [Mac](https://www.gnugk.org/keepalive.html)
* You should check for hs_err_pid error files in the root fs of the agent http://www.oracle.com/technetwork/java/javase/felog-138657.html#gbwcy
* Check the logs in the root filesystem of the agent
 * Disable energy save options that suspend, or hibernate the host
* If you experience Out of Memory issues on the remoting process, try to fix the memory of the remoting process to at least 128MB (JVM options -Xms<MEM_SYZE> and -Xmx<MEM_SYZE>).
* Avoid to use slow network filesystems (<100MB/s) for the agent work directory. This impacts performance.
* If you connect several jenkins nodes to the same host, you should use different user and work directory for each one, to avoid concurrence issues.

### Common info needed to troubleshooting a bug

In order to try to replicate an issue reported in Jira, we need the following info. Also, keep in mind that Jenkins Jira is not a support site, see [How to report an issue](https://wiki.jenkins-ci.org/display/JENKINS/How+to+report+an+issue)

* Jenkins core version
* OS you use on your SSH agents
* OpenSSH version you have installed on your SSH agents?
* Attach the agent connection log (http://jenkins.example.com/computer/NODENAME/log)
* Attach the logs inside the remoting folder (see [remoting work directory](https://github.com/jenkinsci/remoting/blob/master/docs/workDir.md#remoting-work-directory) )? 
* Could you attach the agent configuration (http://jenkins.example.com/computer/NODENAME/config.xml) file?
* Attach the exception on Jenkins logs associated with the fail
* Attach the exception on build logs associated with the fail
* Attach a thread dump captured when the issue is exposed [Obtaining a thread dump](https://wiki.jenkins.io/display/JENKINS/Obtaining+a+thread+dump)
* Are your SSH agents static or provisioned by a cloud plugin (k8s, Mesos, Docker, EC2, Azure, ...)?
* Do it happen only on the SSH agents?
* Do it happen on all SSH agents or only on a few? Is there something in common between those SSH agents?
* Do you see if it happens always with the same job or type of job?

### Force disconnection

In some cases the agent appears as connected but is not, and the disconnect button is not present, in those cases you
can force the disconnection of the agent by using an URL like this one `http://jenkins.example.com/jenkins/computer/NODE_NAME/disconnect`

### Threads stuck at CredentialsProvider.trackAll

If you detect an adnormal number of threads and the thread dump showed a thread for each offline agent stuck waiting for a lock like this:

```
at hudson.XmlFile.write(XmlFile.java:186)
at hudson.model.Fingerprint.save(Fingerprint.java:1301)
at hudson.model.Fingerprint.save(Fingerprint.java:1245)

locked hudson.model.Fingerprint@40e3a4f1
at hudson.BulkChange.commit(BulkChange.java:98)
at com.cloudbees.plugins.credentials.CredentialsProvider.trackAll(CredentialsProvider.java:1533)
at com.cloudbees.plugins.credentials.CredentialsProvider.track(CredentialsProvider.java:1478)
at hudson.plugins.sshslaves.SSHLauncher.launch(SSHLauncher.java:856)
locked hudson.plugins.sshslaves.SSHLauncher@57dc4a8a
...
```

You may want to disable the credentials tracking by setting the property `-Dhudson.plugins.sshslaves.SSHLauncher.trackCredentials=false`
in the Jenkins properties. it can be set in runtime by executing the following code in the Jenkins script console but the change is not permanent.

```
System.setProperty("hudson.plugins.sshslaves.SSHLauncher.trackCredentials","false");
```
