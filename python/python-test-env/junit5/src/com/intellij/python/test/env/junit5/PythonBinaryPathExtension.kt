// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.junit5

import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.jetbrains.python.PythonBinary
import org.jetbrains.annotations.ApiStatus.Internal
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver

/**
 * Extension that provides [PythonBinary] parameter annotated with [com.intellij.python.junit5Tests.framework.env.PythonBinaryPath].
 * Retrieves the Python environment initialized by [RunOnEnvironmentsExtension].
 */
@Internal
internal class PythonBinaryPathExtension : ParameterResolver {

  override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
    return parameterContext.parameter.type == PythonBinary::class.java &&
           parameterContext.parameter.isAnnotationPresent(PythonBinaryPath::class.java)
  }

  override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): PythonBinary {
    return RunOnEnvironmentsExtension.getPythonEnvironment(extensionContext).pythonPath
  }
}
