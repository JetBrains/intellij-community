
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
    1.5 or above (this is to execute Maven - it still allows you to build against 1.3
    and prior JDK's).
  Memory:
    No minimum requirement.
  Disk:
    No minimum requirement. Approximately 100MB will be used for your local repository,
    however this will vary depending on usage and can be removed and redownloaded at
    any time.
  Operating System:
    No minimum requirement. On Windows, Windows NT and above or Cygwin is required for
    the startup scripts. Tested on Windows XP, Fedora Core and Mac OS X.

  Installing Maven
  ----------------

  1) Unpack the archive where you would like to store the binaries, eg:

    Unix-based Operating Systems (Linux, Solaris and Mac OS X)
      tar zxvf apache-maven-3.0.x.tar.gz
    Windows 2000/XP
      unzip apache-maven-3.0.x.zip

  2) A directory called "apache-maven-3.0.x" will be created.

  3) Add the bin directory to your PATH, eg:

    Unix-based Operating Systems (Linux, Solaris and Mac OS X)
      export PATH=/usr/local/apache-maven-3.0.x/bin:$PATH
    Windows 2000/XP
      set PATH="c:\program files\apache-maven-3.0.x\bin";%PATH%

  4) Make sure JAVA_HOME is set to the location of your JDK

  5) Run "mvn --version" to verify that it is correctly installed.

  For complete documentation, see http://maven.apache.org/download.html#Installation

  Licensing
  ---------

  Please see the file called LICENSE.TXT

  Maven URLS
  ----------

  Home Page:          http://maven.apache.org/
  Downloads:          http://maven.apache.org/download.html
  Release Notes:      http://maven.apache.org/release-notes.html
  Mailing Lists:      http://maven.apache.org/mail-lists.html
  Source Code:        http://svn.apache.org/repos/asf/maven/
  Issue Tracking:     http://jira.codehaus.org/browse/MNG
  Wiki:               http://docs.codehaus.org/display/MAVENUSER/
  Available Plugins:  http://maven.apache.org/plugins/index.html
