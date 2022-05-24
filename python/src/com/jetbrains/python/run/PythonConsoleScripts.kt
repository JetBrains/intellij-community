// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PythonConsoleScripts")

package com.jetbrains.python.run

import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.value.*
import com.intellij.execution.util.ProgramParametersConfigurator
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.python.console.PyConsoleOptions
import com.jetbrains.python.console.getPathMapper
import com.jetbrains.python.sdk.PythonEnvUtil
import com.jetbrains.python.sdk.PythonSdkUtil
import org.jetbrains.annotations.ApiStatus
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
fun buildScriptWithConsoleRun(config: PythonRunConfiguration): String = buildString {
  val configEnvs = config.envs
  configEnvs.remove(PythonEnvUtil.PYTHONUNBUFFERED)
  if (configEnvs.isNotEmpty()) {
    append("import os\n")
    for ((key, value) in configEnvs) {
      append("os.environ[${key.toStringLiteral()}] = ${value.toStringLiteral()}\n")
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
  append("runfile(").append(scriptPath.toStringLiteral())
  val scriptParameters = ProgramParametersConfigurator.expandMacrosAndParseParameters(config.scriptParameters)
  if (scriptParameters.isNotEmpty()) {
    append(", args=[").append(scriptParameters.joinToString(separator = ", ", transform = String::toStringLiteral)).append("]")
  }
  if (!workingDir.isEmpty()) {
    append(", wdir=").append(workingDir.toStringLiteral())
  }
  if (config.isModuleMode) {
    append(", is_module=True")
  }
  append(")")
}

private val EMPTY_STRING: TargetEnvironmentFunction<String> = constant("")

/**
 * Composes the function that produces lines for execution in Python REPL for running Python script specified in the given [config].
 *
 * Uses `runfile()` method defined in `_pydev_bundle/pydev_umd.py`.
 *
 * @param config Python run configuration with the information about Python script, its arguments, environment variables and the working
 *               directory
 *
 * @see buildScriptWithConsoleRun
 */
@ApiStatus.Experimental
fun buildScriptFunctionWithConsoleRun(config: PythonRunConfiguration): TargetEnvironmentFunction<String> {
  val configEnvs = config.envs
  configEnvs.remove(PythonEnvUtil.PYTHONUNBUFFERED)
  var result = EMPTY_STRING
  if (configEnvs.isNotEmpty()) {
    result += "import os\n"
    for ((key, value) in configEnvs) {
      result += "os.environ[${key.toStringLiteral()}] = ${value.toStringLiteral()}'\n"
    }
  }
  val scriptPath = config.scriptName
  val workingDir = config.workingDirectory
  result += "runfile("
  result += getTargetEnvironmentValueForLocalPath(scriptPath).toStringLiteral()
  val scriptParameters = ProgramParametersConfigurator.expandMacrosAndParseParameters(config.scriptParameters)
  if (scriptParameters.size != 0) {
    result += ", args=[" + scriptParameters.joinToString<String>(separator = ", ", transform = String::toStringLiteral) + "]"
  }
  if (!workingDir.isEmpty()) {
    result += ", wdir="
    result += getTargetEnvironmentValueForLocalPath(workingDir).toStringLiteral()
  }
  if (config.isModuleMode) {
    result += ", is_module=True"
  }
  result += ")"
  return result
}

@Contract(pure = true)
private fun escape(s: String): String = StringUtil.escapeCharCharacters(s)

/**
 * Returns this [String] as Python string literal.
 *
 * Whitespaces, backslashes and single quotes are escaped and the escaped string is wrapped in single quotes.
 */
@Contract(pure = true)
fun String.toStringLiteral() = "'${escape(this)}'"

@Contract(pure = true)
private fun TargetEnvironmentFunction<String>.toStringLiteral(): TargetEnvironmentFunction<String> =
  StringLiteralTargetFunctionWrapper(this)

private class StringLiteralTargetFunctionWrapper(private val s: TargetEnvironmentFunction<String>)
  : TraceableTargetEnvironmentFunction<String>() {
  override fun applyInner(t: TargetEnvironment): String = s.apply(t).toStringLiteral()

  override fun toString(): String {
    return "StringLiteralTargetFunctionWrapper(s=$s)"
  }
}