// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.EnvironmentUtil
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.python.sdk.PythonSdkType
import java.io.File

/**
 * @author traff
 */


class PyVirtualEnvReader(val virtualEnvSdkPath: String) : EnvironmentUtil.ShellEnvReader() {
  private val LOG = Logger.getInstance("#com.jetbrains.python.run.PyVirtualEnvReader")

  companion object {
    val virtualEnvVars = listOf("PATH", "PS1", "VIRTUAL_ENV", "PYTHONHOME", "PROMPT", "_OLD_VIRTUAL_PROMPT", "_OLD_VIRTUAL_PYTHONHOME",
                                "_OLD_VIRTUAL_PATH")
  }

  // in case of Conda we need to pass an argument to an activate script that tells which exactly environment to activate
  val activate: Pair<String, String?>? = findActivateScript(virtualEnvSdkPath, shell)

  override fun getShell(): String? {
    if (File("/bin/bash").exists()) {
      return "/bin/bash";
    }
    else
      if (File("/bin/sh").exists()) {
        return "/bin/sh";
      }
      else {
        return super.getShell();
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

fun findActivateScript(path: String?, shellPath: String?): Pair<String, String?>? {
  val shellName = if (shellPath != null) File(shellPath).name else null
  val activate = if (SystemInfo.isWindows) findActivateOnWindows(path)
  else if (shellName == "fish" || shellName == "csh") File(File(path).parentFile, "activate." + shellName)
  else File(File(path).parentFile, "activate")

  return if (activate != null && activate.exists()) {
    val sdk = PythonSdkType.findSdkByPath(path)
    if (sdk != null && PythonSdkType.isCondaVirtualEnv(sdk)) Pair(activate.absolutePath, condaEnvFolder(path))
    else Pair(activate.absolutePath, null)
  }
  else null
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