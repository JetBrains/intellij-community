// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(LowLevelLocalMachineAccess::class)
package com.jetbrains.python.run

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.EnvironmentUtil
import com.intellij.util.ShellEnvironmentReader
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import com.jetbrains.python.packaging.PyCondaPackageService
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name

internal class PyVirtualEnvReader(private val virtualEnvSdkPath: String) {
  companion object {
    private val LOG = Logger.getInstance(PyVirtualEnvReader::class.java)

    @Suppress("SpellCheckingInspection")
    private val virtualEnvVars = listOf(
      "PATH", "PS1", "VIRTUAL_ENV", "PYTHONHOME", "PROMPT", "_OLD_VIRTUAL_PROMPT",
      "_OLD_VIRTUAL_PYTHONHOME", "_OLD_VIRTUAL_PATH", "CONDA_SHLVL", "CONDA_PROMPT_MODIFIER",
      "CONDA_PREFIX", "CONDA_DEFAULT_ENV",
      "GDAL_DATA", "PROJ_LIB", "JAVA_HOME", "JAVA_LD_LIBRARY_PATH"
    )

    /**
     * Filter envs that are set up by the activate script, adding other variables from the different shell can break the actual shell.
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

  @Suppress("PyExceptionTooBroad")
  fun readPythonEnv(): MutableMap<String, String> {
    try {
      if (OS.CURRENT != OS.Windows) {
        val activateScript = activate?.first?.let { Path.of(it) }
        val args = activate?.second?.let { listOf(it) }
        val command = ShellEnvironmentReader.shellCommand(shell, activateScript, /*interactive =*/ false, args)
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
    val shellName = if (shellPath != null) Path.of(shellPath).name else null
    val activate = findActivateInPath(Path.of(sdkPath!!), shellName)
    return if (activate != null && activate.exists()) {
      Pair(activate.toAbsolutePath().toString(), null)
    }
    else null
  }
  else if (@Suppress("DEPRECATION") PythonSdkUtil.isConda(sdkPath)) {
    val condaExecutable = PyCondaPackageService.getCondaExecutable(sdkPath!!)
    if (condaExecutable != null) {
      val activate = findActivateInPath(Path.of(condaExecutable), shellName = null)
      if (activate != null && activate.exists()) {
        return Pair(activate.toAbsolutePath().toString(), condaEnvFolder(sdkPath))
      }
    }
  }

  return null
}

private fun findActivateInPath(path: Path, shellName: String?): Path? = when {
  OS.CURRENT == OS.Windows -> findActivateOnWindows(path)
  shellName == "fish" || shellName == "csh" -> path.resolveSibling("activate.${shellName}")
  else -> path.resolveSibling("activate")
}

private fun condaEnvFolder(path: String): String? =
  Path.of(path).let { if (OS.CURRENT == OS.Windows) it.parent else it.parent?.parent }?.toString()

private fun findActivateOnWindows(path: Path): Path? {
  for (location in arrayListOf("activate.bat", "Scripts/activate.bat")) {
    val file = path.resolveSibling(location)
    if (file.exists()) {
      return file
    }
  }
  return null
}
