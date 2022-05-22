// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PythonConsoleScripts")

package com.jetbrains.python.run

import com.intellij.execution.util.ProgramParametersConfigurator
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.python.console.PyConsoleOptions
import com.jetbrains.python.console.getPathMapper
import com.jetbrains.python.sdk.PythonEnvUtil
import com.jetbrains.python.sdk.PythonSdkUtil
import org.jetbrains.annotations.Contract

/**
 * Composes lines for execution in Python REPL to run Python script specified in the given [config].
 *
 * Uses `runfile()` method defined in `_pydev_bundle/pydev_umd.py`.
 *
 * @param config Python run configuration with the information about Python script, its arguments, environment variables and the working
 *               directory
 * @return lines to be executed in Python REPL
 */
fun buildScriptWithConsoleRun(config: PythonRunConfiguration): String {
  val sb = StringBuilder()
  val configEnvs = config.envs
  configEnvs.remove(PythonEnvUtil.PYTHONUNBUFFERED)
  if (configEnvs.isNotEmpty()) {
    sb.append("import os\n")
    for ((key, value) in configEnvs) {
      sb.append("os.environ['").append(escape(key)).append("'] = '").append(escape(value)).append("'\n")
    }
  }
  val project = config.project
  val sdk = config.sdk
  val pathMapper = getPathMapper(project, sdk, PyConsoleOptions.getInstance(project).pythonConsoleSettings)
  var scriptPath = config.scriptName
  var workingDir = config.workingDirectory
  if (PythonSdkUtil.isRemote(sdk) && pathMapper != null) {
    scriptPath = pathMapper.convertToRemote(scriptPath)
    workingDir = pathMapper.convertToRemote(workingDir)
  }
  sb.append("runfile('").append(escape(scriptPath)).append("'")
  val scriptParameters = ProgramParametersConfigurator.expandMacrosAndParseParameters(config.scriptParameters)
  if (scriptParameters.size != 0) {
    sb.append(", args=[")
    for (i in scriptParameters.indices) {
      if (i != 0) {
        sb.append(", ")
      }
      sb.append("'").append(escape(scriptParameters[i])).append("'")
    }
    sb.append("]")
  }
  if (!workingDir.isEmpty()) {
    sb.append(", wdir='").append(escape(workingDir)).append("'")
  }
  if (config.isModuleMode) {
    sb.append(", is_module=True")
  }
  sb.append(")")
  return sb.toString()
}

@Contract(pure = true)
private fun escape(s: String): String = StringUtil.escapeCharCharacters(s)