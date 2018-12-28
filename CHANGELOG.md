# Changelog

### 1.29.2 (Dec 28, 2018)

* [JENKINS-54884](https://issues.jenkins-ci.org/browse/JENKINS-54884) [JENKINS-54746](https://issues.jenkins-ci.org/browse/JENKINS-54746) Use Trilead ssh2 from Jenkins Core and use Trilead API plugin for test
* [JENKINS-54934](https://issues.jenkins-ci.org/browse/JENKINS-54934) Max number of retries and retry wait time do not allow 0

#### Breaking changes

* see 1.27 and 1.29.0

### 1.29.1 (Nov 20, 2018)

* [JENKINS-54686](https://issues.jenkins-ci.org/browse/JENKINS-54686) partial revert of the change to maintain compatibility with Cloud plugins

#### Breaking changes

* see 1.27 and 1.29.0
* It does not longer use the Trilead-ssh2 library provided by the core, it now uses Trilead-api plugin.
 * **Plugins that have it as dependency (EC2 Fleet, Docker, ...) must test the upgrade to 1.29.0.**
 
### 1.29.0 (Nov 18, 2018)

* [JENKINS-54686](https://issues.jenkins-ci.org/browse/JENKINS-54686) Use trilead-api plugin instead trilead-ssh2 from core
* [JENKINS-49235](https://issues.jenkins-ci.org/browse/JENKINS-49235) Do not record fingerprints while holding the lock
* [JENKINS-54269](https://issues.jenkins-ci.org/browse/JENKINS-54269) MissingVerificationStrategyAdministrativeMonitor is missing its name
* [JENKINS-52015](https://issues.jenkins-ci.org/browse/JENKINS-52015) Empty credentials dropdown when creating new agent

#### Breaking changes

* see 1.27
* It does not longer use the Trilead-ssh2 library provided by the core, it now uses Trilead-api plugin.
 * **This breaks compatibility with plugins that have it as dependency (EC2 Fleet, Docker, ...).**

### 1.28.1 (Sep 5, 2018)

* [JENKINS-53254](https://issues.jenkins-ci.org/browse/JENKINS-53254) SSH connection fails in vShpere cloud plugin with new version of SSH slaves plugin (1.27)

#### Breaking changes

* see 1.27

### 1.28 (Aug 27, 2018)

* [JENKINS-53245](https://issues.jenkins-ci.org/browse/JENKINS-53245) UnsupportedOperationException when setting JAVA_HOME

#### Breaking changes

* see 1.27

### 1.27 (Aug 26, 2018)

* [JENKINS-51876](https://issues.jenkins-ci.org/browse/JENKINS-51876) Update Core and Java version, remove deprecated code
* [JENKINS-52660](https://issues.jenkins-ci.org/browse/JENKINS-52660) Allow to disable TCP_NODELAY
* [JENKINS-42840](https://issues.jenkins-ci.org/browse/JENKINS-42840) Slave to agent Renaming: SSH Slaves Plugin
* [JENKINS-44111](https://issues.jenkins-ci.org/browse/JENKINS-44111) Enable Remoting work dir by default in SSH Slaves Plugin
* [JENKINS-52613](https://issues.jenkins-ci.org/browse/JENKINS-52613), [JENKINS-52739](https://issues.jenkins-ci.org/browse/JENKINS-52739) configure 10 retries, 15 seconds between retries, and a timeout of about 210 seconds to connect to the agent.
* [JENKINS-47586](https://issues.jenkins-ci.org/browse/JENKINS-47586) Removes JDK installer
* [JENKINS-37152](https://issues.jenkins-ci.org/browse/JENKINS-37152) Support Win32-OpenSSH
* [JENKINS-49235](https://issues.jenkins-ci.org/browse/JENKINS-49235) Allow disable credentials tracking
* [JENKINS-53174](https://issues.jenkins-ci.org/browse/JENKINS-53174) Improve documentation

#### Breaking changes

* Requires [Jenkins 2.73.3](https://jenkins.io/changelog-stable/#v2.73.3)
* Requires **JDK/JRE 8 installed** on agents
* The *slave.jar* file copied on the agent work folder change name to *remoting.jar*

### 1.26 (Feb 26, 2018)
* [JENKINS-49607](https://issues.jenkins-ci.org/browse/JENKINS-49607) - Report the required Java version when the plugin cannot find Java on the agent
* [JENKINS-43786](https://issues.jenkins-ci.org/browse/JENKINS-43786) - Adapt the adminisitrative monitor to new design in Jenkins 2.103+
* [PR #82](https://github.com/jenkinsci/ssh-slaves-plugin/pull/82) - Do not lookup for credentials in SSHLauncher constructor so that the launcher can be initialized before the Credentials store is fully loaded (for Configuration-as-Code plugin)

### 1.25.1 (Jan 26, 2018)
* [JENKINS-49032](https://issues.jenkins-ci.org/browse/JENKINS-49032) - Revert usage of "exec" command so that SSH Slaves can connect to Windows agents (regression in 1.25)

### 1.25 (Jan 05, 2018)
* [JENKINS-48616](https://issues.jenkins-ci.org/browse/JENKINS-48616) - Pass connection timeouts to socket connection logic if launch timeout is defined in agent settings
* Replace sh with java rather than launching a new process (reverted in 1.25.1)

### 1.24 (Dec 18, 2017)
* [JENKINS-48613](https://issues.jenkins-ci.org/browse/JENKINS-48613) - Extend the original fix of JENKINS-19465 to prevent the afterDisconnect() handler thread explosion when the SSH connection is locked by the read operation
* [JENKINS-48538](https://issues.jenkins-ci.org/browse/JENKINS-48538) - Prevent NPE in SSHLauncher#isRecoverable() when the exception has empty message
* [JENKINS-48260](https://issues.jenkins-ci.org/browse/JENKINS-48260) - Upgrade the Parent POM to 3.0 and fix Java compatibility tests

### 1.23 (Dec 12, 2017)
* [JENKINS-44893](https://issues.jenkins-ci.org/browse/JENKINS-44893) - Prevent the "java.lang.ClassNotFoundException: hudson.plugins.sshslaves.verifiers.JenkinsTrilead9VersionSupport" error in logs
* [JENKINS-19465](https://issues.jenkins-ci.org/browse/JENKINS-19465) - Prevent agent hanging and piling up of afterDisconnect() handler threads when the agent gets disconnected before the launch completion
* [PR #73](https://github.com/jenkinsci/ssh-slaves-plugin/pull/73) - Add explicit MIT license definition to the pom.xml file

### 1.22 (Oct 16, 2017)
* [JENKINS-47448](https://issues.jenkins-ci.org/browse/JENKINS-47448) - Workaround the issue with default JDKInstaller in the plugin by installing Java jdk-8u144
* [PR #71](https://github.com/jenkinsci/ssh-slaves-plugin/pull/71) - Add Chinese translation

### 1.21 (Aug 18, 2017)
* [JENKINS-29412](https://issues.jenkins-ci.org/browse/JENKINS-29412) - Minimal required Java Level is determined dynamically. Java 8 is required on agents when Jenkins version is 2.54+
* [JENKINS-38832](https://issues.jenkins-ci.org/browse/JENKINS-38832) - Add support for credential usage tracking
* [PR #58](https://github.com/jenkinsci/ssh-slaves-plugin/pull/58) - Remove obsolete reflection code in SSHLauncher
* [PR #53](https://github.com/jenkinsci/ssh-slaves-plugin/pull/53), [PR #56](https://github.com/jenkinsci/ssh-slaves-plugin/pull/56), [PR #57](https://github.com/jenkinsci/ssh-slaves-plugin/pull/57) - Cleanup typos in the documentation and logs
* [PR #64](https://github.com/jenkinsci/ssh-slaves-plugin/pull/64) - The plugin codebase is now explicitly licensed with MIT License 

### 1.20 (Jun 13, 2017)
* [JENKINS-44832](https://issues.jenkins-ci.org/browse/JENKINS-44832) IllegalArgumentException under some conditions after update to 1.18 (or 1.19).

### 1.19 (Jun 12, 2017)
* [JENKINS-44830](https://issues.jenkins-ci.org/browse/JENKINS-44830) NullPointerException after upgrading to 1.18 with slaves configured in 1.14- without a host key verification strategy set since then.

### 1.18 (Jun 12, 2017)
* [JENKINS-42959](https://issues.jenkins-ci.org/browse/JENKINS-42959) Specify preferred host keys during connect.

### 1.17 (Apr 12, 2017)
* issue@43481 Updated JRE version which gets automatically installed to 8u121, allowing this mode to work with Jenkins 2.54+ which no longer runs on Java 7.

### 1.16 (Mar 23, 2017)
* [JENKINS-42969](https://issues.jenkins-ci.org/browse/JENKINS-42969) New Manually trusted key Verification Strategy option introduced in 1.15 did not work in Jenkins 2.30+.

### 1.15 (Mar 20, 2017)
* [SECURITY-161](https://issues.jenkins-ci.org/browse/SECURITY-161) (advisory) Host key verification was not performed.
* [JENKINS-42022](https://issues.jenkins-ci.org/browse/JENKINS-42022) Remove 'unix machines' from description.

### 1.14 (Mar 16, 2017)
* [PR #44](https://github.com/jenkinsci/ssh-slaves-plugin/pull/44) Get rid of IOException2.
* [JENKINS-42022](https://issues.jenkins-ci.org/browse/JENKINS-42022) Remove 'unix machines' from description.

### 1.13 (Jan 28, 2017)
* [PR #41](https://github.com/jenkinsci/ssh-slaves-plugin/pull/41) Do not swallow IOException in case it is not recoverable.
* [JENKINS-40001](https://issues.jenkins-ci.org/browse/JENKINS-40001) Added plugin's description.

### 1.12 (Dec 01, 2016)
* [JENKINS-40092](https://issues.jenkins-ci.org/browse/JENKINS-40092) slave.jar copy via SCP (fallback when SFTP is unavailable or broken) failed starting with Jenkins 2.33.
* [JENKINS-35522](https://issues.jenkins-ci.org/browse/JENKINS-35522) Improved credentials selection.

### 1.11 (Apr 27, 2016)
* Upgrade to new parent pom
* Improve logging
* Use JenkinsRule instead of HudsonTestCase for tests.

### 1.10 (Aug 06, 2015)
* Update JDK version for auto installer
* Timeout the afterDisconnect cleanup thread to prevent deadlock [JENKINS-23560](https://issues.jenkins-ci.org/browse/JENKINS-23560)

### 1.9 (Nov 04, 2014)
* Diagnosability improvements in case of a connection loss. See Remoting issue.

### 1.8 (Oct 07, 2014)
* [SECURITY-158](https://issues.jenkins-ci.org/browse/SECURITY-158) fix.
* German localization updated.

### 1.7.1 (Sep 29, 2014)
* Fix NPE when trying to launch non-reconfigured slaves after upgrade to 1.7 version of plugin.

### 1.7 (Sep 26, 2014)
* Protect against some cases where there is no private key resulting in an NPE (possible fix for [JENKINS-20332](https://issues.jenkins-ci.org/browse/JENKINS-20332))
* Updated help text
* Localization cleanup
* Improved error diagnostics
* Allow connection retries (Pull Request #19)
* Enforce timeout for connection cleanup (possible fix for [JENKINS-14332](https://issues.jenkins-ci.org/browse/JENKINS-14332))

### 1.6 (Feb 5, 2014)
* Add initial connection timeout to prevent stalled connections from preventing slave connection.
* Update credentials plugin to 1.9.4 and ssh-credentials to 1.6.1 to ensure the in-place addition of credentials is available.
* Change the hard-coded JDK from 1.6.0_16 to 1.6.0_45

### 1.5 (Oct 16, 2013)
* Fix to how credentials are sourced for the drop-down list
* Use credentials plugin's *c:select/* so that when credentials plugin adds the ability for in-place credential addition this can be picked up without modifying ssh-slaves

### 1.4 (Oct 8, 2013)
* Fixed issue with Slave log on Jenkins 1.521+ ([JENKINS-19758](https://issues.jenkins-ci.org/browse/JENKINS-19758))

### 1.3 (Oct 4, 2013)
* Reworked the upgrading of credentials logic. Should be much improved and result in a true minimal initial set

### 1.2 (Aug 8, 2013)
* Fixed binary compatibility for plugins depending on this one.

### 1.1 (Aug 7, 2013)
* Forced upgrade of dependency SSH Credentials.

### 1.0 (Aug 7, 2013)
* Upgrade dependencies to SSH Credentials Plugin 1.0 and Credentials Plugin 1.6 and migrated code from legacy data type to the new StandardCredential based types.
>NOTE: It will not be possible to downgrade to previous releases without risking the loss of some configuration data.

### 0.27 (Jun 21, 2013)
* Reduce the # of threads spawned. Even more so with Jenkins 1.521 and onward.

### 0.25 (Apr 17, 2013)
* When upgrading credentials from pre 0.23 format, ensure that the credentials are persisted with the correct security context for persisting system/global credentials (issue #17648)

### 0.24 (Apr 16, 2013)
* Removed some unnecessary debug code that remained as a fragment during development of the bulk data transfer improvements in 0.23
* Added some Japanese localizations
* Prevented persistence of duplicate credentials under some code paths
* Restored support for empty username as indicator of the user that Jenkins is running as.
* Upgrade to latest version of te ssh-credentials plugin. 

### 0.23 (Mar 21, 2013)
* Rely on SSH Credentials Plugin for unified credential handling across different places that use SSH
* Performance improvement on bulk data transfer when used in a large latency/high bandwidth network ([JENKINS-7813](https://issues.jenkins-ci.org/browse/JENKINS-7813))

### 0.22 (Dec 07, 2012)
* Find slave.jar even when running from hudson-dev:run.
* Allow environment variables to be declared in the java path, that are then expanded according to environment variables declared on the node or globally.

### 0.21 (Oct 26, 2011)
* Slave is slow copying maven artifacts to master ([JENKINS-3922](https://issues.jenkins-ci.org/browse/JENKINS-3922)).

### 0.20 (Sep 28, 2011)
* JDK installation on SSH slaves with newer Jenkins was broken ([JENKINS-10641](https://issues.jenkins-ci.org/browse/JENKINS-10641))

### 0.19 (Aug 25, 2011)
* Fixed possible NPE during error recovery
* Improved the error message when the server doesn't support the configured authentication mode ([JENKINS-6714](https://issues.jenkins-ci.org/browse/JENKINS-6714))

### 0.18 (Jul 06, 2011)
* Ability to programmatically control the JDK to be installed

### 0.17 (Jun 13, 2011)
* Fixed an API incompatibility regression introduced in 0.15.

### 0.16 (Apr 28, 2011)
* Improved error diagnostics for unreadable SSH private key file.

### 0.15 (Mar 26, 2011)
* New field to be able to configure the java command to use to start the slave

### 0.14 (Nov 2, 2010)
* Delete file via ssh if SFTP is not available ([JENKINS-7006](https://issues.jenkins-ci.org/browse/JENKINS-7006))

### 0.13 (Aug 13, 2010)
* Added Japanese localization.
* Fixed deprecated api.

### 0.12 (June 1, 2010)
* Avoid "password argument is null" error ([JENKINS-6620](https://issues.jenkins-ci.org/browse/JENKINS-6620))
* Version check of JDKs was broken in locales that don't use '.' as the floating point separator ([JENKINS-6441](https://issues.jenkins-ci.org/browse/JENKINS-6441))
* If SFTP is not available on the slave, use SCP ([JENKINS-6239](https://issues.jenkins-ci.org/browse/JENKINS-6239))
* Hudson fails to detect JVM versions when loading older data ([JENKINS-4856](https://issues.jenkins-ci.org/browse/JENKINS-4856))

### 0.10 (May 2, 2010)
* Launcher was storing password in plaintext ([JENKINS-5363](https://issues.jenkins-ci.org/browse/JENKINS-5363))
* Check node properties for JAVA_HOME and JDK tool path when locating java ([JENKINS-5412](https://issues.jenkins-ci.org/browse/JENKINS-5412))
* Support for openjdk 7 ([JENKINS-6005](https://issues.jenkins-ci.org/browse/JENKINS-6005))

### 0.9 (December 9, 2009)
* JDK auto installation works on Windows+MKS environment (report)

### 0.8 (October 23, 2009)
* Allow OpenJDK in Java discovery (report)
* Added a fool-proof check to detect a garbage in SSH exec session to avoid SFTP packet length problem (report)

### 0.7 (July 27, 2009)
* Supports private keys in the PuTTY format.
* Fixed possible NPE (report)

### 0.6 (July 20, 2009)
* Improved the error reporting if the plugin fails to find usable Java implementation (report)
* User name can be now omitted, which defaults to the user that's running the Hudson master.

### 0.5 (April 28, 2009)
* Added support for specifying the Slave JVM options

### 0.4 (February 2, 2009)
* Unknown

### 0.3 (January 30, 2009)
* Unknown

### 0.2 (June 14, 2008)
* Tidy-ups and i18n enabling the plugin

### 0.1 (June 9, 2008)
* Initial release
