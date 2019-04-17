
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
* Did you check the SSHD service logs on your agent? try to increase the verbosity by setting `LogLevel VERBOSE` or `LogLevel DEBUG1` on your /etc/ssh/sshd_config file and see [Logging_and_Troubleshooting](https://en.wikibooks.org/wiki/OpenSSH/Logging_and_Troubleshooting)
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

### 1.29.0 Breaks compatibility with Cloud plugins that do not use trilead-api plugin as dependency

ssh-slaves-plugins not longer uses trilead-ssh2 module from the Jenkins core so plugins that depends on ssh-slaves-plugin must include trilead-api plugin as dependency until every the plugins change to this dependency. If you find this issue with one of your cloud plugins please report it and downgrade ssh-slaves-plugin to <1.28.1 until the dependency is added to your cloud plugin.

```
SSHLauncher{host='192.168.1.100', port=22, credentialsId='b6a4fe2c-9ba5-4052-b91c-XXXXXXXXX', jvmOptions='-Xmx256m', javaPath='', prefixStartSlaveCmd='', suffixStartSlaveCmd='', launchTimeoutSeconds=210, maxNumRetries=10, retryWaitTime=15, sshHostKeyVerificationStrategy=hudson.plugins.sshslaves.verifiers.ManuallyTrustedKeyVerificationStrategy, tcpNoDelay=true, trackCredentials=false}
[11/20/18 00:29:56] [SSH] Opening SSH connection to 192.168.1.100:22.
[11/20/18 00:29:57] [SSH] SSH host key matches key seen previously for this host. Connection will be allowed.
ERROR: Unexpected error in launching a agent. This is probably a bug in Jenkins.
java.lang.NoClassDefFoundError: com/trilead/ssh2/Connection
	at com.cloudbees.jenkins.plugins.sshcredentials.impl.TrileadSSHPasswordAuthenticator$Factory.supports(TrileadSSHPasswordAuthenticator.java:194)
	at com.cloudbees.jenkins.plugins.sshcredentials.impl.TrileadSSHPasswordAuthenticator$Factory.newInstance(TrileadSSHPasswordAuthenticator.java:181)
	at com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator.newInstance(SSHAuthenticator.java:216)
	at com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator.newInstance(SSHAuthenticator.java:170)
	at hudson.plugins.sshslaves.SSHLauncher.openConnection(SSHLauncher.java:1213)
	at hudson.plugins.sshslaves.SSHLauncher$2.call(SSHLauncher.java:846)
	at hudson.plugins.sshslaves.SSHLauncher$2.call(SSHLauncher.java:833)
	at java.util.concurrent.FutureTask.run(FutureTask.java:266)
	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)
	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624)
	at java.lang.Thread.run(Thread.java:748)
[11/20/18 00:29:57] Launch failed - cleaning up connection
[11/20/18 00:29:57] [SSH] Connection closed. 
```

### After upgrade to ssh-slaves 1.28+ Failed to connect using SSH key credentials from files

The ssh-slaves version newer than 1.28 uses ssh-credentials 1.14, this versions deprecated the use of "From the Jenkins master ~/.ssh", and "From a file on Jenkins master" SSH credential types because [SECURITY-440](https://jenkins.io/security/advisory/2018-06-25/#SECURITY-440), the ssh-credentials plugins should migrate these deprecated credentials to "Enter directly" type on restart but seems there are some cases that it fails or it is not possible. 

The issue is related to ssh-credentials and a deprecated type of credentials, the workaround it is to recreate the credential with the same ID using "Enter directly" for the key, probably if you only save again the credential it will be migrated.

for more details see [JENKINS-54746](https://issues.jenkins-ci.org/browse/JENKINS-54746)

Agent log

```
SSHLauncher{host='HOSTNAME', port=22, credentialsId='XXXXXX', jvmOptions='', javaPath='', prefixStartSlaveCmd='', suffixStartSlaveCmd='', launchTimeoutSeconds=210, maxNumRetries=10, retryWaitTime=15, sshHostKeyVerificationStrategy=hudson.plugins.sshslaves.verifiers.ManuallyTrustedKeyVerificationStrategy, tcpNoDelay=true, trackCredentials=true}
[11/21/18 09:40:05] [SSH] Opening SSH connection to HOSTNAME:22.
[11/21/18 09:40:05] [SSH] SSH host key matches key seen previously for this host. Connection will be allowed.
[11/21/18 09:40:05] [SSH] Authentication failed.
Authentication failed.
[11/21/18 09:40:05] Launch failed - cleaning up connection
[11/21/18 09:40:05] [SSH] Connection closed.
```

Manage old data 

```
ConversionException: Could not call com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey$UsersPrivateKeySource.readResolve() : anonymous is missing the Overall/RunScripts permission : Could not call com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey$UsersPrivateKeySource.readResolve() : anonymous is missing the Overall/RunScripts permission ---- Debugging information ---- message : Could not call com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey$UsersPrivateKeySource.readResolve() : anonymous is missing the Overall/RunScripts permission cause-exception : com.thoughtworks.xstream.converters.reflection.ObjectAccessException cause-message : Could not call com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey$UsersPrivateKeySource.readResolve() : anonymous is missing the Overall/RunScripts permission class : com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey$UsersPrivateKeySource required-type : com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey$UsersPrivateKeySource converter-type : hudson.util.RobustReflectionConverter path : /com.cloudbees.plugins.credentials.SystemCredentialsProvider/domainCredentialsMap/entry/java.util.concurrent.CopyOnWriteArrayList/com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey/privateKeySource line number : 21 -------------------------------
```

### Selenium Grid agents failed to connect

On recent versions of Jenkins Core, [Agent - Master Access Control](https://wiki.jenkins.io/display/JENKINS/Slave+To+Master+Access+Control) was introduced it seems causes an issue with Selenium Grid Agents, to fix this problem you have to disable: "Manage Jenkins" > "Configure Global Security", and check "Enable Agent → Master Access Control" (as it's said in Jenkin documentation)
https://wiki.jenkins.io/display/JENKINS/Slave+To+Master+Access+Control

"On the other hand, if all your agents are trusted to the same degree as your master, then it is safe to leave this subsystem off"

```
Apr 03, 2019 9:46:01 AM org.jenkinsci.remoting.util.AnonymousClassWarnings warn

WARNING: Attempt to (de-)serialize anonymous class hudson.plugins.selenium.configuration.DirectJsonInputConfiguration$1; see: https://jenkins.io/redirect/serialization-of-anonymous-classes/
Apr 03, 2019 9:46:06 AM hudson.remoting.SynchronousCommandTransport$ReaderThread run
INFO: I/O error in channel channel

java.io.IOException: Unexpected termination of the channel
        at hudson.remoting.SynchronousCommandTransport$ReaderThread.run(SynchronousCommandTransport.java:77)
Caused by: java.io.EOFException
        at java.io.ObjectInputStream$PeekInputStream.readFully(ObjectInputStream.java:2681)
        at java.io.ObjectInputStream$BlockDataInputStream.readShort(ObjectInputStream.java:3156)
        at java.io.ObjectInputStream.readStreamHeader(ObjectInputStream.java:862)
        at java.io.ObjectInputStream.<init>(ObjectInputStream.java:358)
        at hudson.remoting.ObjectInputStreamEx.<init>(ObjectInputStreamEx.java:49)
        at hudson.remoting.Command.readFrom(Command.java:140)
        at hudson.remoting.Command.readFrom(Command.java:126)
        at hudson.remoting.AbstractSynchronousByteArrayCommandTransport.read(AbstractSynchronousByteArrayCommandTransport.java:36)
        at hudson.remoting.SynchronousCommandTransport$ReaderThread.run(SynchronousCommandTransport.java:63)
```


