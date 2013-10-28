# PyCharm Community Edition

The "python" directory in the source repository contains the source code of PyCharm Community Edition and the Python plugin for
IntelliJ IDEA Community Edition.

## Skeletons

In order to successfully run the full test suite for PyCharm Community Edition and to build a fully working distribution, you need to
check out the library skeletons, which are stored in a separate Git repository. To do this, you need to change to the 'helpers'
directory and run the following command:

    git clone https://github.com/JetBrains/python-skeletons.git

## Building and Running

The code is part of the main IntelliJ IDEA Community Edition project and is compiled together with the rest of the codebase.
To run PyCharm Community Edition, please use the provided run configuration "PyCharm Community Edition". To run IntelliJ IDEA with
the Python plugin, please use the "IDEA with Python plugin" run configuration.

To run the test suite, use the built-in JUnit test runner and run all tests in the "python-community-tests" module.

## Building from the Command Line

To build the distribution archive of PyCharm Community Edition, execute build.xml Ant build script in this directory. The results of the
build execution can be found at out/artifacts.

## Building the Python Plugin

To build the Python plugin for IntelliJ IDEA Community Edition:

 * Download the .tar.gz distribution of the most recent EAP or release build of IntelliJ IDEA Community Edition;
 * Run the following command:

   ant -Didea.path=<download path> -Didea.build.number=<build number of the build you're using> plugin

The .zip file of the built plugin will be placed at distCE/python-community-<branch number>.SNAPSHOT.zip
