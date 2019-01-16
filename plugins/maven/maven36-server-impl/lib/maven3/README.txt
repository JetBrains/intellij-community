
                          Apache Maven

  What is it?
  -----------

  Maven is a software project management and comprehension tool. Based on
  the concept of a Project Object Model (POM), Maven can manage a project's
  build, reporting and documentation from a central piece of information.

  Documentation
  -------------

  The most up-to-date documentation can be found at https://maven.apache.org/.

  Release Notes
  -------------

  The full list of changes can be found at https://maven.apache.org/docs/history.html.

  System Requirements
  -------------------

  JDK:
    1.7 or above (this is to execute Maven - it still allows you to build against 1.3
    and prior JDK's).
  Memory:
    No minimum requirement.
  Disk:
    Approximately 10MB is required for the Maven installation itself. In addition to
    that, additional disk space will be used for your local Maven repository. The size
    of your local repository will vary depending on usage but expect at least 500MB.
  Operating System:
    Windows:
      Windows 2000 or above.
    Unix based systems (Linux, Solaris and Mac OS X) and others:
      No minimum requirement.

  Installing Maven
  ----------------

  1) Unpack the archive where you would like to store the binaries, e.g.:

    Unix-based operating systems (Linux, Solaris and Mac OS X)
      tar zxvf apache-maven-3.x.y.tar.gz
    Windows
      unzip apache-maven-3.x.y.zip

  2) A directory called "apache-maven-3.x.y" will be created.

  3) Add the bin directory to your PATH, e.g.:

    Unix-based operating systems (Linux, Solaris and Mac OS X)
      export PATH=/usr/local/apache-maven-3.x.y/bin:$PATH
    Windows
      set PATH="c:\program files\apache-maven-3.x.y\bin";%PATH%

  4) Make sure JAVA_HOME is set to the location of your JDK

  5) Run "mvn --version" to verify that it is correctly installed.

  For complete documentation, see https://maven.apache.org/download.html#Installation

  Licensing
  ---------

  Please see the file called LICENSE.

  Maven URLS
  ----------

  Home Page:          https://maven.apache.org/
  Downloads:          https://maven.apache.org/download.html
  Release Notes:      https://maven.apache.org/docs/history.html
  Mailing Lists:      https://maven.apache.org/mail-lists.html
  Source Code:        https://gitbox.apache.org/repos/asf/maven.git
  Issue Tracking:     https://issues.apache.org/jira/browse/MNG
  Wiki:               https://cwiki.apache.org/confluence/display/MAVEN/
  Available Plugins:  https://maven.apache.org/plugins/
