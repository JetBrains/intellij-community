// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import org.apache.tools.ant.taskdefs.condition.Os
import java.net.URL
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.exists

plugins {
  id("com.jetbrains.python.envs") version "0.0.31"
}

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
  "3.6" to if (isWindows) "3.6.8" else "3.6.15",
  "3.7" to "3.7.9",
  "3.8" to "3.8.10",
  "3.9" to "3.9.13",
  "3.10" to "3.10.8",
  "3.11" to "3.11.0",
  "3.12" to "3.12.0",
)

envs {
  bootstrapDirectory = pythonsDirectory
  envsDirectory = venvsDirectory

  // TODO: lift and shifted, consider changing logic
  if (isWindows) {
    // On Windows you almost always want to use cache server except you are outside of JB
    var zipRepositoryURL: URL? = URL(
      System.getenv().getOrDefault("PYCHARM_ZIP_REPOSITORY", "https://packages.jetbrains.team/files/p/py/python-archives-windows/"))
    try {
      zipRepositoryURL!!.content
    }
    catch (e: Exception) {
      zipRepositoryURL = null
      System.err.println("Cache server is unavailable. Will try to build python from scratch.")
    }
    if (zipRepositoryURL != null) {
      zipRepository = zipRepositoryURL
      shouldUseZipsFromRepository = true
    }
  }
}

tasks.register<Exec>("kill_python_processes") {
  onlyIf { isWindows }

  // TODO: looks ugly, how can it be improved?
  commandLine("powershell", """"Get-Process | where {${'$'}_.Name -ieq \"python\"} | Stop-Process"""")
}

tasks.register<Delete>("clean") {
  dependsOn("kill_python_processes")
  mustRunAfter("kill_python_processes")

  delete(project.layout.buildDirectory)
}

tasks.register("build") {
  mustRunAfter("clean")
  dependsOn(tasks.matching { it.name.startsWith("setup_") }, "clean")
}

fun createPython(id: String, version: String?, packages: List<String> = listOf(), tags: List<String> = listOf()) {
  check(version != null)

  // TODO: pythonsDirectory vs venvsDirectory for different types
  val pythonHome = File(pythonsDirectory, id)

  envs {
    // TODO: support different python types
    python(id, pythonVersionMapping[version], packages)
  }

  project.tasks.create("populate_tags_$id") {
    dependsOn(tasks.matching { it.name.matches("Bootstrap_[A-Z]*_'$id'.*".toRegex()) })
    onlyIf { tags.isNotEmpty() }

    doLast {
      val tagsFile = pythonHome.resolve("tags.txt")
      // keep simple message to be able to see step in execution log if performed
      println("Adding tags to: $tagsFile")
      tagsFile.writeText(tags.joinToString(separator = "\n"))
    }
  }

  project.tasks.create("populate_symlinks_$id") {
    dependsOn("populate_tags_$id")

    // as we have non-exact version as a key of hashMap retrieval logic from
    // the old script may be easily omitted (TBD: will we be able to keep clear mapping..?
    // maybe one will ever want to add "myCoolPythonVersion" as a key and break the logic)
    val symlinkPath = pythonHome.resolve("python$version" + if (isWindows) ".exe" else "" ).toPath()
    val executablePath = pythonHome.resolve( if (isWindows) "python.exe" else "bin/python$version").toPath()

    // only if file doesn't exist
    onlyIf { !symlinkPath.exists() }

    doLast {
      println("Generating symlink: $symlinkPath -> $executablePath")
      symlinkPath.createSymbolicLinkPointingTo(executablePath)
    }
  }

  // the task serves as aggregator so that one could just execute `./gradlew setup_python_123`
  // to build some specific environment
  project.tasks.create("setup_$id") {
    mustRunAfter("clean")
    setDependsOn(listOf("clean", "populate_symlinks_$id"))
  }
}

createPython("py36_django_latest", "3.6",
             listOf("django", "behave-django", "behave", "pytest", "untangle"),
             listOf("python3.6", "django", "django20", "behave", "behave-django", "django2", "pytest", "untangle"))

if (isUnix && !isMacOs) {
  createPython("py37",
               "3.7",
               listOf("pyqt5==5.12", "PySide2==5.12.1"),
               listOf("python3.7", "qt"))
}
else {
  // qt is for unix only
  createPython("py37", "3.7",
               listOf(),
               listOf("python3.7"))
}

createPython("py38", "3.8",
             listOf("ipython==7.8", "django==2.2", "behave", "jinja2", "tox>=2.0", "nose", "pytest", "django-nose", "behave-django",
                    "pytest-xdist", "untangle", "numpy", "pandas"),
             listOf("python3.8", "python3", "ipython", "ipython780", "skeletons", "django", "behave", "behave-django", "tox", "jinja2",
                    "packaging", "pytest", "nose", "django-nose", "behave-django", "django2", "xdist", "untangle", "pandas"))

createPython("python3.9", "3.9",
             listOf("pytest", "pytest-xdist"),
             listOf("python3.9", "python3", "pytest", "xdist", "packaging"))

createPython("python3.10", "3.10",
             listOf(), listOf("python3.10"))

createPython("python3.11", "3.11",
             listOf("black == 23.1.0", "joblib", "tensorflow"),
             listOf("python3.11", "black", "joblib", "tensorflow"))

createPython("python3.12", "3.12",
             listOf("teamcity-messages"),
             listOf("python3", "python3.12", "messages"))

createPython("py27", "2.7",
             listOf("tox>=3.8.3", "nose", "pytest", "Twisted", "behave", "teamcity-messages", "untangle")
             // TODO: maybe switch to optional dependency Twisted[windows-platform]
             // https://docs.twisted.org/en/stable/installation/howto/optional.html
             + if (isWindows) listOf("pypiwin32") else listOf(), //win32api is required for pypiwin32
             listOf("python2.7", "nose", "pytest", "behave", "packaging", "tox", "twisted", "django-nose", "untangle", "messages"))
