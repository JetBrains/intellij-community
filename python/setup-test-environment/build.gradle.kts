// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import org.apache.tools.ant.taskdefs.condition.Os
import java.net.URL
import kotlin.io.path.createLinkPointingTo
import kotlin.io.path.exists

plugins {
  id("com.jetbrains.python.envs") version "0.0.31"
}

enum class PythonType { PYTHON, CONDA }
// If you decided to change a default path, make sure to update in `community/python/testSrc/com/jetbrains/env/PyEnvTestSettings.kt`
val pythonsDirectory = File(System.getenv().getOrDefault("PYCHARM_PYTHONS", File(buildDir, "pythons").path))
val venvsDirectory = File(System.getenv().getOrDefault("PYCHARM_PYTHON_VIRTUAL_ENVS", File(buildDir, "envs").path))

/**
 * Installs python interpreters for env. tests using CPython.
 * Utilizes following env variables:
 *
 * PYCHARM_PYTHONS -- path to install cPythons
 * PYCHARM_PYTHON_VIRTUAL_ENVS -- path to install condas
 *
 * PYCHARM_ZIP_REPOSITORY -- to download unpacked pythons for Windows (default cpython does not support unattended installation)
 * Recommended value: https://repo.labs.intellij.net/pycharm/python-archives-windows/
 *
 * Pitfall: TMP var on windows points to very long path inside of user local dir and may lead to errors.
 * It is recommended to create "c:\temp\" with full write access and set TMP="c:\temp\" on Windows.
 *
 * ``PyEnvTestSettings`` class uses these vars also.
 *
 */

val isWindows = Os.isFamily(Os.FAMILY_WINDOWS)
val isUnix = Os.isFamily(Os.FAMILY_UNIX)
val isMacOs = Os.isFamily(Os.FAMILY_MAC)

val pythonVersionMapping = mapOf(
  "2.7" to "2.7.18",
  "3.8" to "3.8.10",
  "3.9" to "3.9.13",
  "3.10" to "3.10.8",
  "3.11" to "3.11.0",
  "3.12" to "3.12.0",
)

val defaultPackages = listOf("virtualenv")

envs {
  bootstrapDirectory = pythonsDirectory
  envsDirectory = venvsDirectory

  // I don't think that it's desired behaviour to install pythons for tests user-wide what will be done
  // if we don't force these options (also there may be conflicts with existing installations)
  zipRepository = URL(System.getenv().getOrDefault("PYCHARM_ZIP_REPOSITORY",
                                                   "https://packages.jetbrains.team/files/p/py/python-archives-windows/"))
  shouldUseZipsFromRepository = isWindows
}

tasks.register<Exec>("kill_python_processes") {
  onlyIf { isWindows }

  // TODO: looks ugly, how can it be improved?
  commandLine("powershell", """"Get-Process | where {${'$'}_.Name -ieq \"python\"} | Stop-Process"""")
}

tasks.register<Delete>("clean") {
  dependsOn("kill_python_processes")

  delete(project.layout.buildDirectory)
  delete(pythonsDirectory)
  delete(venvsDirectory)
}

tasks.register("build") {
  dependsOn(tasks.matching { it.name.startsWith("setup_") }, "clean")
}

fun createPython(
  id: String,
  version: String,
  packages: List<String> = listOf(),
  tags: List<String> = listOf(),
  type: PythonType = PythonType.PYTHON,
) {
  val pythonHome = File(pythonsDirectory, id)
  val packages = packages + defaultPackages

  envs {
    when (type) {
      PythonType.PYTHON -> python(id, pythonVersionMapping[version], packages)
      PythonType.CONDA -> conda(id, version, packages)
    }
  }

  project.tasks.create("populate_tags_$id") {
    dependsOn(tasks.matching { it.name.matches("Bootstrap_[A-Z]*_'$id'.*".toRegex()) })
    onlyIf { tags.isNotEmpty() }

    doLast {
      val tagsFile = pythonHome.resolve("tags.txt")
      println("Adding tags to: $tagsFile")
      tagsFile.writeText(tags.joinToString(separator = "\n"))
    }
  }

  project.tasks.create("populate_links_$id") {
    dependsOn("populate_tags_$id")

    // as we have non-exact version as a key of hashMap retrieval logic from
    // the old script may be easily omitted (TBD: will we be able to keep clear mapping..?
    // maybe one will ever want to add "myCoolPythonVersion" as a key and break the logic)
    val linkPath = pythonHome.resolve("python$version" + if (isWindows) ".exe" else "").toPath()
    val executablePath = pythonHome.resolve(if (isWindows) "python.exe" else "bin/python$version").toPath()

    onlyIf { !linkPath.exists() && type == PythonType.PYTHON }

    doLast {
      println("Generating link: $linkPath -> $executablePath")
      linkPath.createLinkPointingTo(executablePath)
    }
  }

  // the task serves as aggregator so that one could just execute `./gradlew setup_python_123`
  // to build some specific environment
  project.tasks.create("setup_$id") {
    setDependsOn(listOf("clean", "populate_links_$id"))
  }
}

createPython("py312_django_latest", "3.12",
             listOf("django", "behave-django", "behave", "pytest", "untangle", "djangorestframework"),
             listOf("python3.12", "django", "django20", "behave", "behave-django", "django2", "pytest", "untangle"))

val qtTags = mutableListOf<String>()
val qtPackages = mutableListOf<String>()
if (isUnix && !isMacOs) { //qt is for Linux only
  qtPackages.addAll(listOf("pyqt5==5.12", "PySide2==5.12.1"))
  qtTags.add("qt")
}

createPython("py27", "2.7",
             listOf(),
             listOf("python2.7"))

createPython("py38", "3.8",
             listOf("ipython==7.8", "django==2.2", "behave", "jinja2", "tox>=2.0", "nose", "pytest", "django-nose", "behave-django",
                    "pytest-xdist", "untangle", "numpy", "pandas") + qtPackages,
             listOf("python3.8", "python3", "ipython", "ipython780", "skeletons", "django", "behave", "behave-django", "tox", "jinja2",
                    "packaging", "pytest", "nose", "django-nose", "behave-django", "django2", "xdist", "untangle", "pandas") + qtTags)

createPython("python3.9", "3.9",
             listOf("pytest", "pytest-xdist"),
             listOf("python3.9", "python3", "pytest", "xdist", "packaging"))

createPython("python3.10", "3.10",
             listOf("untangle"), listOf("python3.10", "untangle"))

createPython("python3.11", "3.11",
             listOf("black == 23.1.0", "joblib", "tensorflow", "poetry"),
             listOf("python3.11", "black", "poetry", "joblib", "tensorflow"))

createPython("python3.12", "3.12",
             listOf("teamcity-messages", "Twisted", "pytest", "poetry")
             // TODO: maybe switch to optional dependency Twisted[windows-platform]
             // https://docs.twisted.org/en/stable/installation/howto/optional.html
             + if (isWindows) listOf("pypiwin32") else listOf(), //win32api is required for pypiwin32
             listOf("python3", "poetry", "python3.12", "messages", "twisted", "pytest"))

// set CONDA_PATH to conda binary location to be able to run tests
createPython("conda", "Miniconda3-py312_24.5.0-0", listOf(), listOf("conda"), type = PythonType.CONDA)
