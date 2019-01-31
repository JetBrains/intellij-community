// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.EnvironmentUtil
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.python.packaging.PyCondaPackageService
import com.jetbrains.python.sdk.PythonSdkType
import java.io.File

/**
 * @author traff
 */


class PyVirtualEnvReader(val virtualEnvSdkPath: String) : EnvironmentUtil.ShellEnvReader() {
  private val LOG = Logger.getInstance("#com.jetbrains.python.run.PyVirtualEnvReader")

  companion object {
    private val virtualEnvVars = listOf("PATH", "PS1", "VIRTUAL_ENV", "PYTHONHOME", "PROMPT", "_OLD_VIRTUAL_PROMPT",
                                        "_OLD_VIRTUAL_PYTHONHOME", "_OLD_VIRTUAL_PATH", "CONDA_SHLVL", "CONDA_PROMPT_MODIFIER",
                                        "CONDA_PREFIX", "CONDA_DEFAULT_ENV")

    /**
     * Filter envs that are setup by the activate script, adding other variables from the different shell can break the actual shell.
     */
    fun filterVirtualEnvVars(env: Map<String, String>): Map<String, String> {
      return env.filterKeys { k -> virtualEnvVars.any { it.equals(k, true) } }
    }
  }

  // in case of Conda we need to pass an argument to an activate script that tells which exactly environment to activate
  val activate: Pair<String, String?>? = findActivateScript(virtualEnvSdkPath, shell)

  override fun getShell(): String? {
    if (File("/bin/bash").exists()) {
      return "/bin/bash"
    }
    else
      if (File("/bin/sh").exists()) {
        return "/bin/sh"
      }
      else {
        return super.getShell()
      }
  }

  fun readPythonEnv(): MutableMap<String, String> {
    try {
      if (SystemInfo.isUnix) {
        // pass shell environment for correct virtualenv environment setup (virtualenv expects to be executed from the terminal)
        return super.readShellEnv(EnvironmentUtil.getEnvironmentMap())
      }
      else {
        if (activate != null) {
          return readBatEnv(File(activate.first), ContainerUtil.createMaybeSingletonList(activate.second))
        }
        else {
          LOG.error("Can't find activate script for $virtualEnvSdkPath")
        }
      }
    } catch (e: Exception) {
      LOG.warn("Couldn't read shell environment: ${e.message}")
    }

    return mutableMapOf()
  }

  override fun getShellProcessCommand(): MutableList<String> {
    val shellPath = shell

    if (shellPath == null || !File(shellPath).canExecute()) {
      throw Exception("shell:" + shellPath)
    }

    return if (activate != null) {
      val activateArg = if (activate.second != null) "'${activate.first}' '${activate.second}'" else "'${activate.first}'"
      mutableListOf(shellPath, "-c", ". $activateArg")
    }
    else super.getShellProcessCommand()
  }

}

fun findActivateScript(sdkPath: String?, shellPath: String?): Pair<String, String?>? {
  if (PythonSdkType.isVirtualEnv(sdkPath)) {
    val shellName = if (shellPath != null) File(shellPath).name else null
    val activate = findActivateInPath(sdkPath!!, shellName)

    return if (activate != null && activate.exists()) {
        Pair(activate.absolutePath, null)
    } else null
  } else if (PythonSdkType.isConda(sdkPath)) {
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

private fun condaEnvFolder(path: String?) = if (SystemInfo.isWindows) File(path).parent else File(path).parentFile.parent

private fun findActivateOnWindows(path: String?): File? {
  for (location in arrayListOf("activate.bat", "Scripts/activate.bat")) {
    val file = File(File(path).parentFile, location)
    if (file.exists()) {
      return file
    }
  }

  return null
}