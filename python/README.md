# PyCharm Community Edition

The "python" directory in the source repository contains the source code of PyCharm Community Edition and the Python plugin for
IntelliJ IDEA Community Edition.

## Building and Running

The code is part of the main IntelliJ IDEA Community Edition project and is compiled together with the rest of the codebase.
To run PyCharm Community Edition, please use the provided run configuration "PyCharm Community Edition".

To run the test suite, use the built-in JUnit test runner and run all tests in the "python-community-tests" module.

## Building from the Command Line

To build the distribution archive of PyCharm Community Edition, execute build.xml Ant build script in this directory. The results of the
build execution can be found at out/artifacts.
