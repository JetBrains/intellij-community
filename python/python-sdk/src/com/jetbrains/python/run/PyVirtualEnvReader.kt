// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.EnvironmentUtil
import com.intellij.util.ShellEnvironmentReader
import com.jetbrains.python.packaging.PyCondaPackageService
import com.jetbrains.python.sdk.PythonSdkUtil
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

internal class PyVirtualEnvReader(private val virtualEnvSdkPath: String) {
  companion object {
    private val LOG = Logger.getInstance(PyVirtualEnvReader::class.java)

    private val virtualEnvVars = listOf("PATH", "PS1", "VIRTUAL_ENV", "PYTHONHOME", "PROMPT", "_OLD_VIRTUAL_PROMPT",
                                        "_OLD_VIRTUAL_PYTHONHOME", "_OLD_VIRTUAL_PATH", "CONDA_SHLVL", "CONDA_PROMPT_MODIFIER",
                                        "CONDA_PREFIX", "CONDA_DEFAULT_ENV",
                                        "GDAL_DATA", "PROJ_LIB", "JAVA_HOME", "JAVA_LD_LIBRARY_PATH")

    /**
     * Filter envs that are setup by the activate script, adding other variables from the different shell can break the actual shell.
     */
    fun filterVirtualEnvVars(env: Map<String, String>): Map<String, String> {
      return env.filterKeys { k -> virtualEnvVars.any { it.equals(k, true) } }
    }
  }

  private val shell: String? by lazy {
    when {
      Files.exists(Path.of("/bin/bash")) -> "/bin/bash"
      Files.exists(Path.of("/bin/sh")) -> "/bin/sh"
      else -> System.getenv("SHELL")
    }
  }

  // in case of Conda we need to pass an argument to the activation script telling which environment to activate
  val activate: Pair<String, String?>? = findActivateScript(virtualEnvSdkPath, shell)

  fun readPythonEnv(): MutableMap<String, String> {
    try {
      if (SystemInfo.isUnix) {
        val command = ShellEnvironmentReader.shellCommand(shell, activate?.first?.let { Path.of(it) }, activate?.second?.let { listOf(it) })
        // pass shell environment for correct virtualenv environment setup (virtualenv expects to be executed from the terminal)
        command.environment().putAll(EnvironmentUtil.getEnvironmentMap())
        return ShellEnvironmentReader.readEnvironment(command, 0).first
      }
      if (activate != null) {
        val command = ShellEnvironmentReader.winShellCommand(Path.of(activate.first), listOfNotNull(activate.second))
        return ShellEnvironmentReader.readEnvironment(command, 0).first
      }
      LOG.error("Can't find activate script for $virtualEnvSdkPath")
    }
    catch (e: Exception) {
      LOG.warn("Couldn't read shell environment: ${e.message}")
    }

    return mutableMapOf()
  }
}

fun findActivateScript(sdkPath: String?, shellPath: String?): Pair<String, String?>? {
  if (PythonSdkUtil.isVirtualEnv(sdkPath)) {
    val shellName = if (shellPath != null) File(shellPath).name else null
    val activate = findActivateInPath(sdkPath!!, shellName)

    return if (activate != null && activate.exists()) {
      Pair(activate.absolutePath, null)
    }
    else null
  }
  else if (PythonSdkUtil.isConda(sdkPath)) {
    val condaExecutable = PyCondaPackageService.getCondaExecutable(sdkPath!!)

    if (condaExecutable != null) {
      val activate = findActivateInPath(File(condaExecutable).path, null)

      if (activate != null && activate.exists()) {
        return Pair(activate.path, condaEnvFolder(sdkPath))
      }
    }
  }

  return null
}

private fun findActivateInPath(path: String, shellName: String?): File? {
  return if (SystemInfo.isWindows) findActivateOnWindows(path)
  else if (shellName == "fish" || shellName == "csh") File(File(path).parentFile, "activate.$shellName")
  else File(File(path).parentFile, "activate")
}

private fun condaEnvFolder(path: String): String? {
  return if (SystemInfo.isWindows) File(path).parent else File(path).parentFile.parent
}

private fun findActivateOnWindows(path: String): File? {
  for (location in arrayListOf("activate.bat", "Scripts/activate.bat")) {
    val file = File(File(path).parentFile, location)
    if (file.exists()) {
      return file
    }
  }

  return null
}
