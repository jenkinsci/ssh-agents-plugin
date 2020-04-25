# Configuring the SSH Build Agents plugin

This plugin allows you to manage an agent running on \*nix machines over SSH. It adds a new type of agent launch method.
This launch method will

* Open an SSH connection to the specified host as the specified username.
* Check the default version of java for that user.
* Copy the latest remoting.jar via SFTP (falling back to scp if SFTP is not available)
* Start the remoting process.

## Prerequisites
Before you start to use the the plugin with your agents, you need to prepare your agents to run the remoting process.
This means that **you have to install a JDK/JRE 8 on your agent** in order to run the *remoting.jar* process.

The agent should have **enough memory** to run the remoting process and the builds. 

**The agent needs to be reachable from the master**,
You have to be ensure that your Jenkins instance can connect through SSH port to your agent; you can check it from the command line.
You will have to supply an account that can log in on the target machine. No root privileges are required.

Finally the agent should have **permissions to read and write on the work directory**, and **enough disk space** to store *remoting.jar* (~1MB),
logs (~10MB should be enough), and your build workspaces (it depends on your builds, probably a few GB).

## Configure a Node to use the SSH Build Agents plugin

### Create a Node
First of all, we have to create a node. Go to `Manage Jenkins/Manage Nodes`. 

![](images/new-node-button.png)

Then click on the `New Node` button and configure the name and Node type.

![](images/new-node-config.png)

After creating the new node, you have to configure the node settings.

* **Name:** Name that uniquely identifies an agent within this Jenkins installation.
* **Description:** Optional human-readable description for this agent. 
* **\# of Executors:** The maximum number of concurrent builds that Jenkins may perform on this agent.
* **Remote root directory:** An agent needs to have a directory dedicated to Jenkins. 
Specify the path to this directory on the agent. It is best to use an absolute path, 
such as /var/jenkins or c:\jenkins. This should be a path local to the agent machine.
* **Labels:** Labels (or tags) are used to group multiple agents into one logical group. 
You will use them to select it as agent for a build. Multiple labels must be separated by a space.
* **Usage:** Controls how Jenkins schedules builds on this node.
    * **Use this node as much as possible:** This is the default setting. In this mode, Jenkins uses this node freely.
    Whenever there is a build that can be done by using this node, Jenkins will use it.
    * **Only build jobs with label expressions matching this node:**
    In this mode, Jenkins will only build a project on this node when that project is restricted to certain nodes using a label expression
    and that expression matches this node's name and/or labels.
    This allows a node to be reserved for certain kinds of jobs. For example, if you have jobs that run performance tests,
    you may want them to only run on a specially configured machine, while preventing all other jobs from using that machine.
    To do so, you would restrict where the test jobs may run by giving them a label expression matching that machine. 
    Furthermore, if you set the # of executors value to 1, you can ensure that only one performance test will execute at 
    any given time on that machine; no other builds will interfere.
* **Launch method:** Select *Launch agents via SSH* Starts a agent by sending commands over a secure SSH connection.

![](images/ssh-node-basic-config.png)

### Configure Launch agents via SSH

Once you selected the **Launch method** to **Launch agents via SSH**, you can configure your SSH agent settings.

#### Required settings

* **Host:** Hostname or IP of the agent, it should be resolvable and reachable from the Jenkins instance. 
* **Credentials:** Select the credentials to be used for logging in to the remote host. See [Integration with SSH Credentials Plugin](#integration-with-ssh-credentials-plugin)
* **Host Key Verification Strategy:** Controls how Jenkins verifies the SSH key presented by the remote host whilst connecting. See [Host Key Verification Strategy](#host-key-verification-strategy)

![](images/ssh-node-launcher-config.png)

#### Advanced settings

* **Port:** The TCP port on which the agent's SSH daemon is listening, usually 22.
* **JavaPath** This Java path will be used to start the JVM. (/mycustomjdkpath/bin/java) If empty Jenkins will search Java command in the agent.
* **JVM Options** Additional arguments for the JVM such as min and max heap size, garbage collector options, and other tuning settings.
* **Prefix Start Agent Command** What you enter here will be prepended to the launch command.
* **Suffix Start Agent Command** What you enter here will be appended to the launch command.
* **Connection Timeout in Seconds** Set the timeout value for ssh agent launch in seconds.
If empty, it will be reset to default value (210 seconds). This will only set the timeout for agent launching; once launched,
the timeout will not apply.
* **Maximum Number of Retries** Set the number of times the SSH connection will be retried if the initial connection results in an error.
If empty, it will be reset to default value (10). If the value is 0, the retries would disabled.
* **Seconds To Wait Between Retries** Set the number of seconds to wait between retry attempts of the initial SSH connection. The default value is 15 seconds.
* **Use TCP_NODELAY flag on the SSH connection** Enable/Disables the TCP_NODELAY flag on the SSH connection.
If set, disable the Nagle algorithm. This means that segments are always sent as soon as possible,
even if there is only a small amount of data. When not set, data is buffered until there is a sufficient amount to send out,
thereby avoiding the frequent sending of small packets, which results in poor utilization of the network.
* **Remoting work directory** The remoting work directory is an internal data storage, which may be used by Remoting to store caches,
logs and other metadata. For more details see [Remoting Work directory](https://github.com/jenkinsci/remoting/blob/master/docs/workDir.md#remoting-work-directory)
If remoting parameter "-workDir PATH" is set in Suffix Start Agent Command this field will be ignored.
If empty, the **Remote root directory** is used as **Remoting Work directory**

![](images/ssh-node-advanced-config.png)

### Host Key Verification Strategy

Controls how Jenkins verifies the SSH key presented by the remote host whilst connecting.

#### Known hosts file Verification Strategy

![](images/hkvs-known-hosts.png)

Checks the known_hosts file (~/.ssh/known_hosts) for the user Jenkins is executing under 
to see if an entry exists that matches the current connection. It is possibel to change the default file by setting
the Java property `-Dhudson.plugins.sshslaves.verifiers.KnownHostsFileKeyVerificationStrategy.known_hosts_file=PATH_TO_FILE`

This method does not make any updates to the Known Hosts file, instead using the file as a read-only source and expecting
someone with suitable access to the appropriate user account on the Jenkins master to update the file as required,
potentially using the ssh hostname command to initiate a connection and update the file appropriately.

#### Manually provided key Verification Strategy

![](images/hkvs-manual-key.png)

Checks the key provided by the remote host matches the key set by the user who configured this connection.

The SSH key expected for this connection. This key should be in the form `algorithm value`
where algorithm is one of ssh-rsa or ssh-dss, and value is the Base 64 encoded content of the key. The keys should be placed in /etc/ssh/<key_name>.pub

#### Manually trusted key Verification Strategy

![](images/hkvs-trusted-key.png)

Checks the remote key matches the key currently marked as trusted for this host.

Depending on configuration, the key will be automatically trusted for the first connection, 
or an authorised user will be asked to approve the key.
An authorised user will be required to approve any new key that gets presented by the remote host.

Require a user with Computer.CONFIGURE permission to authorise the key presented during the first connection to 
this host before the connection will be allowed to be established.

If this option is not enabled then the key presented on first connection for this host will be automatically trusted
and allowed for all subsequent connections without any manual intervention.

#### Non verifying Verification Strategy

![](images/hkvs-no-verify.png)

Does not perform any verification of the SSH key presented by the remote host, allowing all connections regardless of the key they present.

### Availability

#### Keep this agent online as much as possible
In this mode, Jenkins will keep this agent online as much as possible.
If the agent goes offline, e.g. due to a temporary network failure, Jenkins will periodically attempt to restart it.

![](images/availability-as-much.png)

#### Take this agent online and offline at specific times
In this mode, Jenkins will bring this agent online at the scheduled time(s), remaining online for a specified amount of time.
If the agent goes offline while it is scheduled to be online, Jenkins will periodically attempt to restart it.

After this agent has been online for the number of minutes specified in the Scheduled Uptime field, it will be taken offline. 
If Keep online while builds are running is checked, and the agent is scheduled to be taken offline, 
Jenkins will wait for any any builds that may be in progress to complete.

![](images/availability-schedule.png)

#### Take this agent online when in demand, and offline when idle
In this mode, Jenkins will bring this agent online if there is demand, i.e. there are queued builds which meet the following criteria:
* They have been in the queue for at least the specified *In demand delay time period*
* They can be executed by this agent (e.g. have a matching label expression)

This agent will be taken offline if:
* There are no active builds running on this agent
* This agent has been idle for at least the specified *Idle delay time period*

![](images/availability-on-demand.png)

### Integration with SSH Credentials Plugin
This plugin is now integrated with the [SSH Credentials Plugin](https://plugins.jenkins.io/ssh-credentials). 
This changes how agents are configured.
The Node configuration is simplified, e.g. you now just have a Credentials drop down listing all the "Global" and 
"System" scoped credentials.
 
If you are upgrading from a previous 0.23 version, the plugin should try to inject any required SSH credentials in the 
[Credentials Plugin](https://plugins.jenkins.io/credentials) using the credentials that were previously stored in each node's definition.

![](images/ssh-credentials-select.png)

To define credentials to use when connecting agents you need to go to the `Jenkins/Manage Jenkins/Manage Credentials` screen.

![](images/ssh-credentials-manage.png)

Once on this screen you can add **SSH credentials**, either using a *Username & Password* or using a *Username & Private Key*.

![](images/ssh-credentials-manage-detail.png)

**NOTE** Not all PEM formats are supported, compare the header of your key with the following supported formats:
* `-----BEGIN OPENSSH PRIVATE KEY-----`
* `-----BEGIN RSA PRIVATE KEY-----`
* `-----BEGIN EC PRIVATE KEY-----`
* `-----BEGIN DSA PRIVATE KEY-----`
In case your key format is not supported you can use `openssl` or `ssh-keygen` to convert it to the SSH protocol version 2 format (RFC4716).

Credential scope controls where the credentials can be used:

* System scope is only available for the root Jenkins instance (in other words Jenkins can use it to connect build nodes, but the credentials are not available to build jobs)
* Global scope is available for the root Jenkins instance and any child items (in other words Jenkins can use it to connect build nodes, build jobs can use it for other SSH Credentials enabled plugins) 
When you have a lot of different credentials it can be useful to put those credentials into credential domains, e.g.

![](images/ssh-credentials-domain.png)

The drop-down for selecting credentials will construct a specification that includes the URI Scheme of ssh 
and the specified hostname and port, so where you have created the appropriate credential domains the choice of credentials 
will be restricted to those outside of any credential domain and those from matching credential domains.
This can help differentiate between multiple keys/password associated with the same username.

### Using Cygwin
See [SSH Build Agents and Cygwin](https://wiki.jenkins.io/display/JENKINS/SSH+slaves+and+Cygwin) for the discussion of how to use this plugin to talk to Cygwin SSHD server.

[Remoting documentation](https://github.com/jenkinsci/remoting/tree/master/docs)

### Launch Windows agents using Microsoft OpenSSH

The current version of the plugin does not run directly on PowerShell, you have to use prefix and suffix settings to trick the command and make it works, Windows 10 machines can run as SSH agents with the Microsoft OpenSSH server by using:

**Prefix Start Agent Command**

```
powershell -Command "cd C:\J\S ; C:\J\S\jdk\bin\java.exe -jar remoting.jar" ; exit 0 ; rem '
```
**Suffix Start Agent Command**

```
'
```

[see this Mark Waite's comment](https://issues.jenkins-ci.org/browse/JENKINS-42856?focusedCommentId=355486&page=com.atlassian.jira.plugin.system.issuetabpanels%3Acomment-tabpanel#comment-355486)
