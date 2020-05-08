// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run

import com.intellij.execution.target.value.TargetEnvironmentFunction
import com.intellij.execution.target.value.constant
import org.jetbrains.annotations.ApiStatus
import java.nio.charset.Charset

/**
 * Represents the Python script or module to be executed and its parameters.
 */
@ApiStatus.Experimental
sealed class PythonExecution {
  var workingDir: TargetEnvironmentFunction<String?>? = null

  val parameters: MutableList<TargetEnvironmentFunction<String>> = mutableListOf()

  val envs: MutableMap<String, TargetEnvironmentFunction<String>> = mutableMapOf()

  var charset: Charset? = null

  fun addParameter(value: String) {
    addParameter(constant(value))
  }

  fun addParameter(value: TargetEnvironmentFunction<String>) {
    parameters.add(value)
  }

  fun addParameters(vararg parameters: String) {
    parameters.forEach { parameter -> addParameter(parameter) }
  }

  fun addEnvironmentVariable(key: String, value: String) {
    envs[key] = constant(value)
  }

  fun addEnvironmentVariable(key: String, value: TargetEnvironmentFunction<String>) {
    envs[key] = value
  }

  /**
   * Java alternative for [PythonExecution] sealed Kotlin class functionality.
   */
  abstract fun accept(visitor: Visitor)

  interface Visitor {
    fun visit(pythonScriptExecution: PythonScriptExecution)

    fun visit(pythonModuleExecution: PythonModuleExecution)
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