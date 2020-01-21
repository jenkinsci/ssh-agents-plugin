# SSH Build Agents Plugin and Cygwin

| WARNING: This documentation was moved from Jenkins Wiki, and it might be obsolete. Any contributions are welcome, just submit a pull request! |
| --- |

## Overview

Cygwin is a Unix emulation library for Windows.
It allows source code written for POSIX environment to be compiled with Cygwin stub files into Windows executables.
When these Cygwin-enabled libraries run, a DLL gets loaded into these processes that implement all the POSIX APIs on top of Win32 APIs.

Because of the way it works, Cygwin's presence does not change the way Java virtual machines work.
JVMs that run on Cygwin-enabled Windows continue to behave exactly the same way as JVMs that run on Cygwin-less Windows.
It will continue to use '\\' as the separator, not '/', `java.io.File` will not suddenly start understanding Unix path, etc.
The path translation from the UNIX style to the Windows style happens within Cygwin DLL.
Programs that are not compiled against Cygwin will not load Cygwin DLL, and as such they will not go through the path translation.

So when you use Cygwin and mix native Windows programs and Cygwin-compiled programs, you need to be mindful when and where the path conversions happen.

For example, when you execute Cygwin-compiled processes (say `bash.exe`), these programs expect Unix-style paths in their command line.
The same applies when we talk to Cygwin-compiled processes over various protocols, such as SFTP.

To make this further confusing, native Windows APIs recognizes '\\' as the directory separator, in addition to '/'.
Simiarly, Cygwin-emulated POSIX APIs accept Windows paths, in addition to the Unix paths.
This helpful "smart" behaviour sometimes makes it difficult for users to understand where the path translation is really happening.

# SSH Build Agents Plguin and Cygwin SSHD.

Cygwin comes with OpenSSH server, which works well with the SSH Build Agents.
This is one of the recommended way of controlling Windows agents from Jenkins, if you don't mind the added effort of [sshd](http://www.noah.org/ssh/cygwin-sshd.html) :

1. Download [cygwin](https://cygwin.com/install.html) with the following packages:  (Admin) **cygrunsrv**, and (Net) **openssh**
2.  Open a cygwin shell window and run the SSH configure: `ssh-host-config -y`
3.  Run the SSH daemon : `cygrunsrv -S cygsshd`
4.  Check that your firewall allow TCP port 22
5.  Java must be available from your SSH client: for example, add a symbolic link, e.g :  
`cd /usr/local/bin && ln -s /cygdrive/c/Program\\ Files\\(x86\\)/Java/jre1.8.0\_211/bin/java.exe java`

When you use SSH launcher to launch an agent on Cygwin-enabled Windows,
you should still specify Windows style path as the remote FS root (such as `c:\jenkins`).
This is because the agent JVM that eventually gets launched doesn't receive the Cygwin path translation.
If you specify Unix style path (such as `/cygdrive/c/jenkins`),
then Jenkins will end up trying to create both `c:\jenkins` (when it copies over `agent.jar` via SFTP) and `c:\cygdrive\c\jenkins` (when agent JVM actually starts and copy more files.)

If you run Jenkins on behalf of other users,
you'll discover that some of your users will not understand when and where the path translation happens,
and will inevitably write build scripts that break.
You can explain to them what's going on, or you can surrender and use [mklink](http://technet.microsoft.com/en-us/library/cc753194.aspx) to create a symlink or junction point that maps `c:\cygdrive\c\jenkins` to `c:\jenkins`.
This will make those broken scripts work happily.

## Treating Cygwin agents like Unix agents

Sometimes you want to treat Cygwin agents like real Unix agents, such as running shell scripts.
When you do it, you often see error messages like this:

    java.io.IOException: Cannot run program "/bin/bash" (in directory "c:\test\workspace\foo"):
      CreateProcess error=3, The system cannot find the path specified

This is because Jenkins is trying to call Windows API and execute "/bin/bash" without going through Cygwin path translation.
Windows interprets `/bin/bash` as `c:\bin\bash.exe`, and unless that path exists, it will fail.

In this case, what's needed is to have Jenkins perform the Cygwin path translation without relying on Cygwin DLL.
This is what [Cygpath Plugin](https://plugins.jenkins.io/cygpath) does;
it checks if a Windows agent have Cygwin, and if you try to run executable that looks like Unix path name, it'll use Cygwin to translate that into its Windows path before calling Windows API.

## Further Reading

Jenkins agents running on Cygwin-enabled Windows are still susceptible to all the other problems Windows agents face. 
See [My software builds on my computer but not on Jenkins](https://wiki.jenkins.io/display/JENKINS/My+software+builds+on+my+computer+but+not+on+Jenkins)
for the discussion of those, including desktop and network drive access.
