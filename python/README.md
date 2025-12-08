[![official JetBrains project](http://jb.gg/badges/official.svg)](https://github.com/JetBrains/.github/blob/main/profile/README.md) [![IntelliJ IDEA build status](https://github.com/JetBrains/intellij-community/workflows/IntelliJ%20IDEA/badge.svg)](https://github.com/JetBrains/intellij-community/actions/workflows/IntelliJ_IDEA.yml) [![PyCharm build status](https://github.com/JetBrains/intellij-community/workflows/PyCharm/badge.svg)](https://github.com/JetBrains/intellij-community/actions/workflows/PyCharm.yml)

# IntelliJ Platform Open-Source Repository (PyCharm)

This repository is the open-source part of the codebase for JetBrains IDEs.

The "python" directory in the source repository contains the source code of PyCharm and the Python plugin for IntelliJ IDEA.

If you are new to the community and would like to contribute code or help others learn, see [CONTRIBUTING.md](https://github.com/JetBrains/intellij-community/blob/master/CONTRIBUTING.md) to get started.

The following conventions will be used to refer to directories on your machine:
* `<USER_HOME>` is your OS user's home directory.
* `<IDEA_HOME>` is the root directory for the IntelliJ Platform source code.

___
## Getting the source code

This section will guide you through the process of obtaining the project sources and help you avoid several common issues associated with git config. It will then walk you through the other steps you must complete before opening the source code in the IDE.

*Follow the steps in the [Getting the source code](https://github.com/JetBrains/intellij-community?tab=readme-ov-file#getting-the-source-code) section of the IntelliJ IDEA instructions.*

---
## Building PyCharm

These instructions will help you build PyCharm from the source code. For it to work, you’ll need to use IntelliJ IDEA **2023.2** or a more recent version.

### Opening the IntelliJ Platform source code in the IDE

*Follow the steps in the [Opening the IntelliJ IDEA source code in the IDE](https://github.com/JetBrains/intellij-community?tab=readme-ov-file#opening-the-intellij-idea-source-code-in-the-ide) section of the IntelliJ IDEA instructions.*

### Build configuration steps
The code is part of the main IntelliJ IDEA project and is compiled together with the rest of the codebase.

*Follow the steps in the [Build configuration steps](https://github.com/JetBrains/intellij-community?tab=readme-ov-file#build-configuration-steps) section of the IntelliJ IDEA instructions.*

### Building the PyCharm application from source

**To build PyCharm from source**, select the provided *PyCharm* run configuration and choose *Build | Build Project* from the main menu.

**To build installation packages**, run the [installers.cmd](https://github.com/JetBrains/intellij-community/blob/master/installers.cmd) script in `<IDEA_HOME>/python/` directory. The `installers.cmd` script is compatible with both Windows and Unix systems. Options to build installers are passed as system properties to the `installers.cmd` command.
You may find the list of available properties in [BuildOptions.kt](https://github.com/JetBrains/intellij-community/blob/master/platform/build-scripts/src/org/jetbrains/intellij/build/BuildOptions.kt)

Installer build examples:
```bash
# Build installers only for current operating system:
./installers.cmd -Dintellij.build.target.os=current
```
```bash
# Build source code _incrementally_ (do not build what was already built before):
./installers.cmd -Dintellij.build.incremental.compilation=true
```

> The `installers.cmd` script is used to run [PyCharmCommunityInstallersBuildTarget.kt](https://github.com/JetBrains/intellij-community/blob/master/python/build/src/PyCharmCommunityInstallersBuildTarget.kt) from the command line.
> You may call it directly from IntelliJ IDEA by using the *Build PyCharm Community Installers (current OS)* run configuration.


#### Dockerized build environment
To build installation packages inside a Docker container with preinstalled dependencies and tools, run the following command in the `<IDEA_HOME>` directory (on Windows, use PowerShell):
```bash
docker run --rm -it --user "$(id -u)" --volume "${PWD}:/community" "$(docker build --quiet . --target pycharm)"
```


> Remember to specify the `--user "$(id -u)"` argument so that the container's user matches the host's user.
> This prevents issues with permissions for the checked-out repository, the build output, and the mounted Maven cache, if any.
>
To reuse the existing Maven cache from the host system, add the following option to the `docker run` command:
`--volume "$HOME/.m2:/home/ide_builder/.m2"`

---
## Running PyCharm
To run the PyCharm that was built from source, select the preconfigured *PyCharm* run configuration and choose *Run | Run* from the main menu. To run IntelliJ IDEA with the Python plugin, use the `IntelliJ IDEA with Python plugin` run configuration.

To run tests on the build, apply the following settings on the *Run | Edit Configurations | Templates | JUnit* configuration tab:
* Working dir: `<IDEA_HOME>/bin`
* VM options: `-ea`


#### Running PyCharm in a CI/CD environment

To run tests outside of IntelliJ IDEA, run the `tests.cmd` command in the `<IDEA_HOME>` directory. The `tests.cmd` command can be used in both Windows and Unix systems.
Options to run tests are passed as system properties to the `tests.cmd` command.
You may find the list of available properties in [TestingOptions.kt](https://github.com/JetBrains/intellij-community/blob/master/platform/build-scripts/src/org/jetbrains/intellij/build/TestingOptions.kt).

```bash
# Build source code _incrementally_ (do not build what was already built before): `
./tests.cmd -Dintellij.build.incremental.compilation=true
```
```bash
#Run a specific test: 
./tests.cmd -Dintellij.build.test.patterns=com.intellij.util.ArrayUtilTest
```

The `tests.cmd` command is used to run [CommunityRunTestsBuildTarget](https://github.com/JetBrains/intellij-community/blob/master/build/src/CommunityRunTestsBuildTarget.kt) from the command line.
You may call it directly from IntelliJ IDEA; see the *tests in community* run configuration for an example.