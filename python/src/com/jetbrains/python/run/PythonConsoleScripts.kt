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
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract
import java.nio.file.Path

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
  val project = config.project
  val sdk = config.sdk
  val pathMapper = getPathMapper(project, sdk, PyConsoleOptions.getInstance(project).pythonConsoleSettings)
  return buildScriptFunctionWithConsoleRun(
    config,
    ::StringScriptBuilder,
    t = { it },
    toTargetPath = { pathMapper?.convertToRemote(it) ?: it },
    toStringLiteral = String::toStringLiteral
  )
}

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
fun buildScriptFunctionWithConsoleRun(config: PythonRunConfiguration): TargetEnvironmentFunction<String> =
  buildScriptFunctionWithConsoleRun(
    config,
    ::TargetEnvironmentFunctionScriptBuilder,
    t = ::constant,
    toTargetPath = { targetPath(Path.of(it)) },
    toStringLiteral = TargetEnvironmentFunction<String>::toStringLiteral
  )

/**
 * @param config Python run configuration
 * @param stringBuilderConstructor allows to instantiate [ScriptBuilder] required for building the result
 * @param t transforms the provided [String] to a [T] object that denotes the string constant
 * @param toTargetPath transforms the provided [String] local path to a [T] object defining corresponding path on the target
 * @param toStringLiteral transforms the provided [T] object to an escaped Python literal [T] object
 */
private fun <T> buildScriptFunctionWithConsoleRun(config: PythonRunConfiguration,
                                                  stringBuilderConstructor: () -> ScriptBuilder<T>,
                                                  t: (String) -> T,
                                                  toTargetPath: (String) -> T,
                                                  toStringLiteral: (T) -> T): T {
  val scriptBuilder = stringBuilderConstructor()
  val configEnvs = config.envs
  configEnvs.remove(PythonEnvUtil.PYTHONUNBUFFERED)
  if (configEnvs.isNotEmpty()) {
    scriptBuilder.append(t("import os\n"))
    for ((key, value) in configEnvs) {
      scriptBuilder.append(t("os.environ[${key.toStringLiteral()}] = ${value.toStringLiteral()}\n"))
    }
  }
  val scriptPath = config.scriptName
  val workingDir = config.workingDirectory
  scriptBuilder.append(t("runfile("))
  scriptBuilder.append(toStringLiteral(toTargetPath(scriptPath)))
  val scriptParameters = ProgramParametersConfigurator.expandMacrosAndParseParameters(config.scriptParameters)
  if (scriptParameters.size != 0) {
    scriptBuilder.append(t(", args=[" + scriptParameters.joinToString<String>(separator = ", ", transform = String::toStringLiteral) + "]"))
  }
  if (!workingDir.isEmpty()) {
    scriptBuilder.append(t(", wdir="))
    scriptBuilder.append(toStringLiteral(toTargetPath(workingDir)))
  }
  if (config.isModuleMode) {
    scriptBuilder.append(t(", is_module=True"))
  }
  scriptBuilder.append(t(")"))
  return scriptBuilder.build()
}

/**
 * This is a temporary interface for smoother transition to Targets API. Its lifetime is expected to be limited by the lifetime of the
 * legacy implementation based on `GeneralCommandLine`.
 */
private interface ScriptBuilder<T> {
  fun append(fragment: T)

  fun build(): T
}

private class StringScriptBuilder : ScriptBuilder<String> {
  private val stringBuilder = StringBuilder()

  override fun append(fragment: String) {
    stringBuilder.append(fragment)
  }

  override fun build(): String = stringBuilder.toString()
}

private class TargetEnvironmentFunctionScriptBuilder : ScriptBuilder<TargetEnvironmentFunction<String>> {
  private var functionBuilder: TargetEnvironmentFunction<String> = constant("")

  override fun append(fragment: TargetEnvironmentFunction<String>) {
    functionBuilder += fragment
  }

  override fun build(): TargetEnvironmentFunction<String> = functionBuilder
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
fun TargetEnvironmentFunction<String>.toStringLiteral(): TargetEnvironmentFunction<String> =
  StringLiteralTargetFunctionWrapper(this)

private class StringLiteralTargetFunctionWrapper(private val s: TargetEnvironmentFunction<String>)
  : TraceableTargetEnvironmentFunction<String>() {
  override fun applyInner(t: TargetEnvironment): String = s.apply(t).toStringLiteral()

  override fun toString(): String {
    return "StringLiteralTargetFunctionWrapper(s=$s)"
  }
}