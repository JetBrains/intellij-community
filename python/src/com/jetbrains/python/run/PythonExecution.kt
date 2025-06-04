// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.value.TargetEnvironmentFunction
import com.intellij.execution.target.value.constant
import com.intellij.openapi.vfs.encoding.EncodingManager
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Path

/**
 * Represents the Python script or module to be executed and its parameters.
 */
@ApiStatus.Experimental
sealed class PythonExecution {
  /**
   * Path to workdir on target
   */
  var workingDir: TargetEnvironmentFunction<out FullPathOnTarget?>? = null

  /** Parameters that return [SKIP_ARGUMENT] are removed from the resulting argument list. */
  val parameters: MutableList<TargetEnvironmentFunction<String>> = mutableListOf()

  val envs: MutableMap<String, TargetEnvironmentFunction<String>> = mutableMapOf()

  var charset: Charset = EncodingManager.getInstance().defaultConsoleEncoding

  var inputFile: File? = null

  /** A place to store additional interpreter parameters, that are not set in a run configuration.

   * Originally added to pass though additional parameters from [com.jetbrains.python.debugger.PyDebugRunner]
   * which originally were lost.
   *
   * @see PythonCommandLineState.startProcess
   * */
  val additionalInterpreterParameters: MutableList<String> = mutableListOf()

  fun addParameter(value: String) {
    addParameter(constant(value))
  }

  /** If the function returns [SKIP_ARGUMENT], the parameter will be removed from the resulting argument list. */
  fun addParameter(value: TargetEnvironmentFunction<String>) {
    parameters.add(value)
  }

  fun addParameters(vararg parameters: String) {
    parameters.forEach { parameter -> addParameter(parameter) }
  }

  fun addParameters(parameters: List<String>) {
    parameters.forEach { parameter -> addParameter(parameter) }
  }

  fun addEnvironmentVariable(key: String, value: String) {
    envs[key] = constant(value)
  }

  fun addEnvironmentVariable(key: String, value: TargetEnvironmentFunction<String>) {
    envs[key] = value
  }

  fun withInputFile(file: File) {
    inputFile = file
  }

  /**
   * Java alternative for [PythonExecution] sealed Kotlin class functionality.
   */
  abstract fun accept(visitor: Visitor)

  interface Visitor {
    fun visit(pythonScriptExecution: PythonScriptExecution)

    fun visit(pythonModuleExecution: PythonModuleExecution)

    fun visit(pythonToolScriptExecution: PythonToolScriptExecution)

    fun visit(pythonToolModuleExecution: PythonToolModuleExecution)
  }

  companion object {
    /** See docs for [parameters]. */
    // python -c 'print(repr(__import__("random").randbytes(16))[2:-1])'
    const val SKIP_ARGUMENT = """\xdc'S>\x02\x03%\x14\xee\xc0\xa1`\xcb\r\xf0\x95"""
  }
}

@ApiStatus.Experimental
class PythonScriptExecution : PythonExecution() {
  var pythonScriptPath: TargetEnvironmentFunction<String>? = null

  override fun accept(visitor: Visitor) = visitor.visit(this)
}

@ApiStatus.Experimental
class PythonModuleExecution : PythonExecution() {
  var moduleName: String? = null

  override fun accept(visitor: Visitor) = visitor.visit(this)
}

@ApiStatus.Internal
sealed class PythonToolExecution(
  val toolPath: Path,
  val toolParams: List<String>,
) : PythonExecution() {
  override fun accept(visitor: Visitor) {
    when (this) {
      is PythonToolScriptExecution -> visitor.visit(this)
      is PythonToolModuleExecution -> visitor.visit(this)
    }
  }
}

@ApiStatus.Internal
class PythonToolScriptExecution(
  toolPath: Path,
  toolParams: List<String>,
  val pythonScriptPath: TargetEnvironmentFunction<Path>
) : PythonToolExecution(toolPath, toolParams) {
  override fun accept(visitor: Visitor) = visitor.visit(this)
}

@ApiStatus.Internal
class PythonToolModuleExecution(
  toolPath: Path,
  toolParams: List<String>,
  val moduleName: String,
  val moduleFlag: String
) : PythonToolExecution(toolPath, toolParams) {
  override fun accept(visitor: Visitor) = visitor.visit(this)
}