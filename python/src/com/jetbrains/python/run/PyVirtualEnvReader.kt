/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.run

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.EnvironmentUtil
import com.intellij.util.LineSeparator
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

  override fun readShellEnv(): MutableMap<String, String> {
    if (SystemInfo.isUnix) {
      return super.readShellEnv()
    }
    else {
      if (activate != null) {
        return readVirtualEnvOnWindows(activate);
      }
      else {
        LOG.error("Can't find activate script for $virtualEnvSdkPath")
        return mutableMapOf();
      }
    }
  }

  private fun readVirtualEnvOnWindows(activate: Pair<String, String?>): MutableMap<String, String> {
    val activateFile = FileUtil.createTempFile("pycharm-virualenv-activate.", ".bat", false)
    val envFile = FileUtil.createTempFile("pycharm-virualenv-envs.", ".tmp", false)
    try {
      FileUtil.copy(File(activate.first), activateFile);
      FileUtil.appendToFile(activateFile, "\n\nset >" + envFile.absoluteFile)

      val command = if (activate.second != null) listOf<String>(activateFile.path, activate.second!!)
      else listOf<String>(activateFile.path)

      return runProcessAndReadEnvs(command, envFile, LineSeparator.CRLF.separatorString)
    }
    finally {
      FileUtil.delete(activateFile)
      FileUtil.delete(envFile)
    }

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