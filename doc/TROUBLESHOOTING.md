
## Troubleshooting

### Common Pitfalls
#### Login profile files
When the SSH slaves plugin connects to a slave, it does not run an interactive shell. 
Instead it does the equivalent of running `ssh slavehost command...` a few times, 
eventually running `ssh slavehost java -jar ...`. Exactly what happens on the slave as a result of this depends on the SSHD implementation, OpenSSH runs this with `bash -c command ...` (or whatever your login shell is.)

This means some of your login profiles that set up your environment are not read by your shell. See [this post](http://stackoverflow.com/questions/216202/why-does-an-ssh-remote-command-get-fewer-environment-variables-then-when-run-man) for more details.

If your login shell does not understand the command syntax used (e.g. the fish shell), use the advanced options **Prefix Start Slave Command** and **Suffix Start Slave Command** to wrap the slave command in e.g. `sh -c " and "`.

Example: For loading `~/.bash_profile` on macOS Slaves set **Prefix Start Slave Command** to `source ~/.bash_profile && ` (intended whitespace at the end).
Make sure to reconnect the slave after changing the slaves commands.

#### Overall recommendations:
* Use a JDK version in the same major version as the Jenkins instance and Agents and preferably a close minor version.
* Tune the TCP stack on of Jenkins instance and Agents
    * [Linux](http://tldp.org/HOWTO/TCP-Keepalive-HOWTO/usingkeepalive.html)
    * [Windows](https://blogs.technet.microsoft.com/nettracer/2010/06/03/things-that-you-may-want-to-know-about-tcp-keepalives/)
    * [Mac](https://www.gnugk.org/keepalive.html)
* You should check for hs_err_pid error files in the root fs of the agent http://www.oracle.com/technetwork/java/javase/felog-138657.html#gbwcy
* Check the logs in the root filesystem of the agent
 * Disable energy save options that suspend, or hibernate the host

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

You may want to disable the credentials tracking by setting the property `-Dhudson.plugins.sshslaves.SSHLauncher.trackCredentials=true`
in the Jenkins properties. it can be set in runtime by executing the following code in the Jenkins script console but the change is not permanent.

```
System.setProperty("hudson.plugins.sshslaves.SSHLauncher.trackCredentials","false");
```
