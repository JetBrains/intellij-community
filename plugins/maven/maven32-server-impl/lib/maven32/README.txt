
                          Apache Maven

  What is it?
  -----------

  Maven is a software project management and comprehension tool. Based on
  the concept of a Project Object Model (POM), Maven can manage a project's
  build, reporting and documentation from a central piece of information.

  Documentation
  -------------

  The most up-to-date documentation can be found at http://maven.apache.org/.

  Release Notes
  -------------

  The full list of changes can be found at http://maven.apache.org/release-notes.html.

  System Requirements
  -------------------

  JDK:
    1.6 or above (this is to execute Maven - it still allows you to build against 1.3
    and prior JDK's).
  Memory:
    No minimum requirement.
  Disk:
    Approximately 10MB is required for the Maven installation itself. In addition to
    that, additional disk space will be used for your local Maven repository. The size
    of your local repository will vary depending on usage but expect at least 500MB.
  Operating System:
    No minimum requirement. Start up scripts are included as shell scripts and Windows
    batch files.

  Installing Maven
  ----------------

  1) Unpack the archive where you would like to store the binaries, eg:

    Unix-based operating systems (Linux, Solaris and Mac OS X)
      tar zxvf apache-maven-3.x.y.tar.gz
    Windows
      unzip apache-maven-3.x.y.zip

  2) A directory called "apache-maven-3.x.y" will be created.

  3) Add the bin directory to your PATH, eg:

    Unix-based operating systems (Linux, Solaris and Mac OS X)
      export PATH=/usr/local/apache-maven-3.x.y/bin:$PATH
    Windows
      set PATH="c:\program files\apache-maven-3.x.y\bin";%PATH%

  4) Make sure JAVA_HOME is set to the location of your JDK

  5) Run "mvn --version" to verify that it is correctly installed.

  For complete documentation, see http://maven.apache.org/download.html#Installation

  Licensing
  ---------

  Please see the file called LICENSE.

  Maven URLS
  ----------

  Home Page:          http://maven.apache.org/
  Downloads:          http://maven.apache.org/download.html
  Release Notes:      http://maven.apache.org/release-notes.html
  Mailing Lists:      http://maven.apache.org/mail-lists.html
  Source Code:        https://git-wip-us.apache.org/repos/asf/maven.git/apache-maven
  Issue Tracking:     http://jira.codehaus.org/browse/MNG
  Wiki:               https://cwiki.apache.org/confluence/display/MAVEN/
  Available Plugins:  http://maven.apache.org/plugins/index.html
