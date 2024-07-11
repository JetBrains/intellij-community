// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.junit5.env.impl

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.io.toNioPathOrNull
import com.jetbrains.env.PyEnvTestSettings
import com.jetbrains.python.junit5.env.PythonBinaryPath
import com.jetbrains.python.sdk.PythonSdkUtil
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.opentest4j.TestAbortedException
import java.nio.file.Path
import kotlin.io.path.isExecutable

/**
 * Looks for the first env python and provides it as an argument
 */
internal class PythonEnvExtension : ParameterResolver {
  private val pythonBinaryPath: Path

  init {
    val pythonSdkPath = PyEnvTestSettings.fromEnvVariables().pythons.firstOrNull()?.toPath()
    if (pythonSdkPath == null) {
      val error = "No python found. See ${PyEnvTestSettings::class} class for more info"
      // Logging due to IDEA-356206
      thisLogger().warn(error)
      throw TestAbortedException(error)
    }

    this.pythonBinaryPath = PythonSdkUtil.getPythonExecutable(pythonSdkPath.toString())!!.toNioPathOrNull()!!
    assert(pythonBinaryPath.isExecutable()) { "$pythonBinaryPath isn't executable" }
  }


  override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
    parameterContext.parameter.type == Path::class.java && parameterContext.parameter.isAnnotationPresent(PythonBinaryPath::class.java)

  override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Path = pythonBinaryPath
}